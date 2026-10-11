package be.anton.curatedphone.data

import android.content.Context
import be.anton.curatedphone.BuildConfig
import be.anton.curatedphone.admin.PolicyManager
import be.anton.curatedphone.approval.ApprovalVerifier
import be.anton.curatedphone.network.*
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

class PhoneRepository(private val context: Context, private val database: AppDatabase) {
    val dao = database.policies()
    val vault = CredentialVault(context)
    val backend = BackendClient(vault)
    val manager = PolicyManager(context, dao)
    private val verifier = ApprovalVerifier(BuildConfig.APPROVAL_KEY_ID, BuildConfig.APPROVAL_PUBLIC_KEY)
    private val mutex = Mutex()
    suspend fun savedState(): DeviceState? = dao.metadata("lastState")?.let { AppJson.decodeFromString(it) }
    suspend fun savedDraft(): Snapshot? = dao.draft()?.let { AppJson.decodeFromString(it.snapshot) }
    suspend fun active(): Snapshot? = dao.active()?.let { AppJson.decodeFromString(it.snapshot) }
    suspend fun saveDraft(snapshot: Snapshot) { dao.saveDraft(DraftEntity(snapshot = AppJson.encodeToString(snapshot))) }
    suspend fun initialDraft(primary: String, backup: String): Snapshot {
        val seed = context.assets.open("default-blocked-apps.json").bufferedReader().use { AppJson.decodeFromString<Seed>(it.readText()) }
        return Snapshot(Policy(blockedAppPackageIds = seed.blockedAppPackageIds, categories = listOf("social", "streaming", "gambling", "adult")), primary, backup).also { saveDraft(it) }
    }
    suspend fun pair(code: String): DeviceState {
        check(vault.token() == null) { "This installation is already paired" }
        val result = AppJson.decodeFromString<PairResult>(backend.call("/device/pair", buildJsonObject { put("code", code.trim()); put("platform", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}; Android ${android.os.Build.VERSION.RELEASE}") }))
        vault.saveToken(result.token); vault.saveDeviceId(result.deviceId)
        initialDraft(result.primaryEmail, result.backupEmail)
        return sync()
    }
    suspend fun sync(): DeviceState {
        val state = AppJson.decodeFromString<DeviceState>(backend.call("/device/state"))
        check(state.deviceId == vault.deviceId()) { "Backend device identity differs from paired device" }
        dao.saveMetadata(MetadataEntity("lastState", AppJson.encodeToString(state)))
        dao.saveMetadata(MetadataEntity("lastSync", System.currentTimeMillis().toString()))
        state.history.forEach { dao.saveRequest(RequestEntity(it.requestId, AppJson.encodeToString(it))) }
        if (savedDraft() == null) {
            val local = active()
            if (local != null) saveDraft(local)
            else if (state.approvedPolicyVersion == 0L) initialDraft(state.guardians.first { it.role == "primary" }.email, state.guardians.first { it.role == "recovery" }.email)
            else error("Approved local data is missing. Native rules are preserved; deliberate recovery is required.")
        }
        return state
    }
    suspend fun submit(snapshot: Snapshot, purpose: String = "settings", recovery: Boolean = false): DeviceState {
        val state = sync()
        be.anton.curatedphone.approval.PolicyValidator.validate(snapshot.policy, manager.protectedPackages())
        backend.call("/requests", buildJsonObject { put("snapshot", AppJson.encodeToJsonElement(snapshot)); put("purpose", purpose); put("recovery", recovery); put("basePolicyVersion", state.approvedPolicyVersion); put("baseGuardianVersion", state.guardianVersion) })
        return sync()
    }
    suspend fun verify(request: RequestInfo, challenge: Challenge, code: String): DeviceState {
        val result = AppJson.decodeFromString<VerifyResult>(backend.call("/requests/verify", buildJsonObject { put("requestId", request.requestId); put("channel", challenge.channel); put("generation", challenge.generation); put("code", code) }))
        if (!result.ok) error(result.error ?: "Code was rejected")
        result.approval?.let { acceptAndApply(it, request) }
        return sync()
    }
    suspend fun resend(requestId: String, channel: String): DeviceState { backend.call("/requests/resend", buildJsonObject { put("requestId", requestId); put("channel", channel) }); return sync() }
    suspend fun cancel(requestId: String): DeviceState { backend.call("/requests/cancel", buildJsonObject { put("requestId", requestId) }); return sync() }
    suspend fun acceptAndApply(envelope: Envelope, request: RequestInfo) = mutex.withLock {
        val p = verifier.verifySignature(envelope)
        val journal = dao.journal(p.approvalId)
        if (journal != null) { check(journal.envelope == AppJson.encodeToString(envelope)); resume(journal); return@withLock }
        val active = dao.active()
        val checked = verifier.verify(envelope, vault.deviceId() ?: error("Pair the phone first"), request, active?.version ?: 0, active?.guardianVersion ?: 0)
        val saved = ApplyJournalEntity(checked.approvalId, AppJson.encodeToString(envelope), AppJson.encodeToString(checked), "applying", "authorized")
        dao.saveJournal(saved)
        resume(saved)
    }
    private suspend fun resume(original: ApplyJournalEntity) {
        var journal = original
        val envelope = AppJson.decodeFromString<Envelope>(journal.envelope)
        val payload = verifier.verifySignature(envelope)
        check(payload.deviceId == vault.deviceId() && payload.approvalId == journal.approvalId && AppJson.encodeToString(payload) == journal.payload)
        if (journal.status != "applied") {
            try {
                if (!(payload.purpose == "remove_management" && journal.stage == "release_ownership" && !manager.isOwner)) {
                    manager.apply(payload) { stage -> journal = journal.copy(status = "applying", stage = stage, failure = null); dao.saveJournal(journal) }
                }
                database.withTransaction {
                    dao.saveActive(PolicyEntity(version = payload.targetPolicyVersion, guardianVersion = payload.targetGuardianVersion, envelope = journal.envelope, snapshot = AppJson.encodeToString(payload.snapshot)))
                    journal = journal.copy(status = "applied", stage = "complete", failure = null)
                    dao.saveJournal(journal)
                    dao.saveHistory(HistoryEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), "Applied", "${payload.purpose}; policy ${payload.targetPolicyVersion}"))
                }
            } catch (e: Exception) {
                val failure = e.message?.take(500) ?: "Native application failed"
                journal = journal.copy(status = "failed", failure = failure); dao.saveJournal(journal)
                runCatching { acknowledge(payload, false, failure) }
                throw e
            }
        }
        if (!journal.acknowledged) { acknowledge(payload, true); dao.saveJournal(journal.copy(acknowledged = true)) }
    }
    private suspend fun acknowledge(payload: ApprovalPayload, success: Boolean, failure: String? = null) {
        backend.call("/device/applied", buildJsonObject { put("requestId", payload.requestId); put("approvalId", payload.approvalId); put("version", payload.targetPolicyVersion); put("success", success); if (failure != null) put("failure", failure); if (payload.purpose == "remove_management") put("ownershipReleased", !manager.isOwner) })
    }
    suspend fun reconcile() = mutex.withLock {
        val pending = dao.unfinished()
        if (pending.isNotEmpty()) { pending.forEach { resume(it) }; return@withLock }
        val local = dao.active() ?: return@withLock
        val envelope = AppJson.decodeFromString<Envelope>(local.envelope)
        val payload = verifier.verifySignature(envelope)
        check(payload.deviceId == vault.deviceId() && payload.targetPolicyVersion == local.version)
        if (payload.purpose != "remove_management") manager.apply(payload) { }
    }
    fun installedApps(): List<InstalledApp> = context.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0).map {
        InstalledApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName, it.activityInfo.packageName in manager.protectedPackages())
    }.distinctBy { it.packageId }.sortedBy { it.name.lowercase() }
}
