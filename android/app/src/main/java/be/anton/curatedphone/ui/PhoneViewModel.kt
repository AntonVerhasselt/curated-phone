package be.anton.curatedphone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.anton.curatedphone.CuratedApplication
import be.anton.curatedphone.approval.PolicyValidator
import be.anton.curatedphone.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhoneUiState(val state: DeviceState? = null, val draft: Snapshot? = null, val active: Snapshot? = null, val apps: List<InstalledApp> = emptyList(), val owner: Boolean = false, val knox: String = "Checking Knox support", val paired: Boolean = false, val busy: Boolean = false, val message: String? = null, val localVersion: Long = 0, val lastSync: Long? = null, val ruleCount: Int = 0, val journal: ApplyJournalEntity? = null, val enforcementIssue: String? = null)
class PhoneViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as CuratedApplication).repository
    private val editMutex = kotlinx.coroutines.sync.Mutex()
    private val mutable = MutableStateFlow(PhoneUiState())
    val ui = mutable.asStateFlow()
    init { perform { refreshLocal(); if (repo.vault.token() != null) mutable.update { it.copy(state = repo.sync()) }; refreshLocal() } }
    private suspend fun refreshLocal() {
        val data = withContext(Dispatchers.IO) { PhoneUiState(state = repo.savedState(), draft = repo.savedDraft(), active = repo.active(), apps = repo.installedApps(), owner = repo.manager.isOwner, knox = repo.manager.knox.status(), paired = repo.vault.token() != null, localVersion = repo.dao.active()?.version ?: 0, lastSync = repo.dao.metadata("lastSync")?.toLongOrNull(), ruleCount = repo.dao.metadata("filterRuleCount")?.toIntOrNull() ?: 0, journal = repo.dao.unfinished().firstOrNull(), enforcementIssue = repo.dao.metadata("reconcileFailure")?.takeIf { it.isNotBlank() }) }
        mutable.value = data.copy(busy = mutable.value.busy, message = mutable.value.message)
    }
    private fun perform(block: suspend () -> Unit) { if (mutable.value.busy) return; mutable.update { it.copy(busy = true, message = null) }; viewModelScope.launch { try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { mutable.update { it.copy(message = e.message ?: "This operation could not complete") }; runCatching { refreshLocal() } } finally { mutable.update { it.copy(busy = false) } } } }
    fun pair(code: String) = perform { repo.pair(code); refreshLocal() }
    fun sync() = perform { repo.sync(); refreshLocal() }
    fun retry() = perform {
        val state = repo.sync()
        if (state.approval != null && state.pending != null) repo.acceptAndApply(state.approval, state.pending) else repo.reconcile()
        refreshLocal(); repo.sync(); refreshLocal()
    }
    private fun edit(transform: (Snapshot) -> Snapshot) {
        val current = mutable.value.draft ?: return
        val next = transform(current)
        mutable.update { it.copy(draft = next, message = null) }
        viewModelScope.launch { editMutex.lock(); try { repo.saveDraft(next) } finally { editMutex.unlock() } }
    }
    fun toggleApp(id: String) {
        if (id in repo.manager.protectedPackages() || !Regex("[a-zA-Z][\\w]*(?:\\.[a-zA-Z][\\w]*)+").matches(id)) { mutable.update { it.copy(message = "This package is protected or invalid") }; return }
        edit { s -> s.copy(policy = s.policy.copy(blockedAppPackageIds = (if (id in s.policy.blockedAppPackageIds) s.policy.blockedAppPackageIds - id else s.policy.blockedAppPackageIds + id).distinct().sorted())) }
    }
    fun toggleCategory(category: String) = edit { s -> s.copy(policy = s.policy.copy(categories = (if (category in s.policy.categories) s.policy.categories - category else s.policy.categories + category).sorted())) }
    fun addDomain(value: String) { try { val domain = PolicyValidator.domain(value); edit { s -> s.copy(policy = s.policy.copy(customDomains = (s.policy.customDomains + domain).distinct().sorted())) } } catch (e: Exception) { mutable.update { it.copy(message = e.message) } } }
    fun removeDomain(value: String) = edit { s -> s.copy(policy = s.policy.copy(customDomains = s.policy.customDomains - value)) }
    fun setEmail(primary: Boolean, value: String) = edit { if (primary) it.copy(primaryEmail = value.trim().lowercase()) else it.copy(backupEmail = value.trim().lowercase()) }
    fun setFeed(category: String, url: String, licence: String) {
        if (!url.startsWith("https://") || licence.isBlank()) { mutable.update { it.copy(message = "Enter an HTTPS feed URL and its licence") }; return }
        edit { s -> s.copy(policy = s.policy.copy(feeds = (s.policy.feeds.filterNot { it.category == category } + FeedSource("$category-feed", category, url.trim(), licence.trim())).sortedBy { it.id })) }
    }
    fun submit(purpose: String = "settings", recovery: Boolean = false) = perform {
        val draft = mutable.value.draft ?: error("Pair the phone first")
        val active = mutable.value.active
        val target = when (purpose) {
            "replace_primary" -> (active ?: error("Finish initial setup first")).copy(primaryEmail = draft.primaryEmail)
            "replace_backup" -> (active ?: error("Finish initial setup first")).copy(backupEmail = draft.backupEmail)
            "remove_management" -> active ?: error("No active policy to remove")
            else -> draft
        }
        repo.submit(target, purpose, recovery); refreshLocal()
    }
    fun verify(challenge: Challenge, code: String) = perform { val request = mutable.value.state?.pending ?: error("No pending request"); repo.verify(request, challenge, code); refreshLocal() }
    fun resend(challenge: Challenge) = perform { repo.resend(mutable.value.state?.pending?.requestId ?: error("No pending request"), challenge.channel); refreshLocal() }
    fun cancel() = perform { repo.cancel(mutable.value.state?.pending?.requestId ?: error("No pending request")); refreshLocal() }
    fun discardDraft() = perform { val active = repo.active() ?: error("No approved policy yet"); repo.saveDraft(active); refreshLocal() }
    fun activateKnox(value: String) = perform { repo.manager.knox.activate(value); mutable.update { it.copy(message = "Licence activation requested. Native readback will confirm availability.") }; refreshLocal() }
}
