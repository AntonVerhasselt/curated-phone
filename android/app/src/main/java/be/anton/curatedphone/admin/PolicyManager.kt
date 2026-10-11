package be.anton.curatedphone.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import be.anton.curatedphone.BuildConfig
import be.anton.curatedphone.approval.PolicyValidator
import be.anton.curatedphone.data.*
import be.anton.curatedphone.filter.*
import be.anton.curatedphone.knox.KnoxPolicyAdapter

class PolicyManager(private val context: Context, private val dao: PolicyDao) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, PhoneAdminReceiver::class.java)
    val knox = KnoxPolicyAdapter(context)
    val isOwner get() = dpm.isDeviceOwnerApp(context.packageName)
    private val chrome = ChromePolicyAdapter(dpm, admin, dao, HttpsCategoryFeedProvider(context) { feed ->
        be.anton.curatedphone.network.BackendClient(be.anton.curatedphone.network.CredentialVault(context)).call("/device/feed-status", kotlinx.serialization.json.buildJsonObject {
            put("feedId", kotlinx.serialization.json.JsonPrimitive(feed.source.id)); put("url", kotlinx.serialization.json.JsonPrimitive(feed.source.url)); put("checksum", kotlinx.serialization.json.JsonPrimitive(feed.checksum)); put("fetchedAt", kotlinx.serialization.json.JsonPrimitive(feed.fetchedAt))
        })
    })
    private val restrictions = listOf(UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_DEBUGGING_FEATURES)
    fun protectedPackages(): Set<String> {
        val home = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        return PolicyValidator.protectedPackages + listOfNotNull(home, context.getSystemService(android.telecom.TelecomManager::class.java).defaultDialerPackage)
    }
    suspend fun apply(payload: ApprovalPayload, stage: suspend (String) -> Unit) {
        check(isOwner) { "Device-owner enrolment is required to enforce this approval" }
        if (payload.purpose == "remove_management") { remove(stage); return }
        PolicyValidator.validate(payload.snapshot.policy, protectedPackages())
        check(!context.getSystemService(UserManager::class.java).hasUserRestriction(UserManager.DISALLOW_INSTALL_APPS)) { "A blanket Android app-install restriction conflicts with the approved default-allow policy" }
        stage("protect_controller")
        if (dao.metadata("protectionOriginal") == null) {
            val baseline = kotlinx.serialization.json.buildJsonObject {
                put("uninstall", kotlinx.serialization.json.JsonPrimitive(dpm.isUninstallBlocked(admin, context.packageName)))
                put("userControls", kotlinx.serialization.json.JsonArray(dpm.getUserControlDisabledPackages(admin).map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("restrictions", kotlinx.serialization.json.JsonArray(restrictions.filter { dpm.getUserRestrictions(admin).getBoolean(it) }.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
            dao.saveMetadata(MetadataEntity("protectionOriginal", baseline.toString()))
        }
        dpm.setUninstallBlocked(admin, context.packageName, true)
        dpm.setUserControlDisabledPackages(admin, (dpm.getUserControlDisabledPackages(admin) + context.packageName).distinct())
        check(dpm.isUninstallBlocked(admin, context.packageName) && context.packageName in dpm.getUserControlDisabledPackages(admin)) { "Controller protection readback failed" }
        stage("block_installation")
        knox.reconcile(payload.snapshot.policy.blockedAppPackageIds.toSet())
        stage("suspend_blocked_apps")
        val target = payload.snapshot.policy.blockedAppPackageIds.toSet()
        val previous = dao.metadata("managedSuspensions")?.let { AppJson.decodeFromString<List<String>>(it) }.orEmpty().toSet()
        val installed = target.filter { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }.toSet()
        // Persist the union before the call so removal/retry also covers a partial application.
        dao.saveMetadata(MetadataEntity("managedSuspensions", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), (previous + installed).sorted())))
        val suspendFailures = dpm.setPackagesSuspended(admin, installed.toTypedArray(), true)
        check(suspendFailures.isEmpty()) { "Cannot suspend: ${suspendFailures.joinToString()}" }
        val remove = (previous - target).filter { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
        check(dpm.setPackagesSuspended(admin, remove.toTypedArray(), false).isEmpty()) { "Failed to unblock previously suspended apps" }
        check(installed.all { dpm.isPackageSuspended(admin, it) } && remove.none { dpm.isPackageSuspended(admin, it) }) { "Suspension readback failed" }
        dao.saveMetadata(MetadataEntity("managedSuspensions", AppJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), installed.sorted())))
        stage("apply_websites")
        chrome.apply(payload.snapshot.policy)
        stage("bypass_protection")
        // This gate is a build-time assertion made only after the real Samsung removal and update tests.
        if (!BuildConfig.DEBUG && !BuildConfig.BYPASS_PROTECTION_VALIDATED) error("Daily-use bypass protections await Samsung release validation")
        if (BuildConfig.BYPASS_PROTECTION_VALIDATED) {
            for (restriction in restrictions) dpm.addUserRestriction(admin, restriction)
            check(restrictions.all { dpm.getUserRestrictions(admin).getBoolean(it) }) { "User restriction readback failed" }
        }
        stage("readback_complete")
    }
    @Suppress("DEPRECATION") private suspend fun remove(stage: suspend (String) -> Unit) {
        // Do not weaken the device until a supported complete release has been proven.
        check(BuildConfig.SAMSUNG_REMOVAL_VALIDATED) { "Complete ownership release has not been validated on this Samsung; removal is blocked before cleanup" }
        stage("restore_chrome"); chrome.restore(); dao.saveMetadata(MetadataEntity("filterRuleCount", "0"))
        stage("remove_installation_rules"); knox.reconcile(emptySet())
        stage("unsuspend_managed_apps")
        val tracked = dao.metadata("managedSuspensions")?.let { AppJson.decodeFromString<List<String>>(it) }.orEmpty().filter { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
        check(dpm.setPackagesSuspended(admin, tracked.toTypedArray(), false).isEmpty()); check(tracked.none { dpm.isPackageSuspended(admin, it) })
        stage("restore_controller_protection")
        val original = dao.metadata("protectionOriginal")?.let { AppJson.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject } ?: error("Protection baseline is missing; refuse unsafe cleanup")
        val preserved = (original["restrictions"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }.toSet()
        for (r in restrictions - preserved) dpm.clearUserRestriction(admin, r)
        val controls = (original["userControls"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        dpm.setUserControlDisabledPackages(admin, controls)
        val uninstall = (original["uninstall"] as kotlinx.serialization.json.JsonPrimitive).content.toBoolean()
        dpm.setUninstallBlocked(admin, context.packageName, uninstall)
        check(dpm.getUserControlDisabledPackages(admin).toSet() == controls.toSet() && dpm.isUninstallBlocked(admin, context.packageName) == uninstall)
        check((restrictions - preserved).none { dpm.getUserRestrictions(admin).getBoolean(it) })
        stage("release_ownership")
        dpm.clearDeviceOwnerApp(context.packageName)
        check(!isOwner) { "Ownership release failed; no reset was attempted" }
    }
}
