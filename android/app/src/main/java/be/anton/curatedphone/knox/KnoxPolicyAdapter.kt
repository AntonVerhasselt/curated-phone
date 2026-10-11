package be.anton.curatedphone.knox

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build

class KnoxPolicyAdapter(private val context: Context) {
    val supported get() = Build.MANUFACTURER.equals("samsung", true) && runCatching { Class.forName("com.samsung.android.knox.EnterpriseDeviceManager") }.isSuccess
    private fun policy(): Any {
        check(supported) { "Samsung Knox SDK or supported hardware is unavailable" }
        val cls = Class.forName("com.samsung.android.knox.EnterpriseDeviceManager")
        val manager = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
        return cls.getMethod("getApplicationPolicy").invoke(manager) ?: error("Knox application policy unavailable")
    }
    private fun lists(api: Any, method: String): Map<String, Set<String>> {
        val list = api.javaClass.getMethod(method).invoke(api) as? List<*> ?: error("Knox readback failed")
        return list.filterNotNull().associate { info ->
            val owner = info.javaClass.getField("adminPackageName").get(info) as String
            val entries = info.javaClass.getField("entries").get(info) as? List<*> ?: error("Knox list is unavailable")
            owner to entries.filterIsInstance<String>().toSet()
        }
    }
    fun status(): String = if (!supported) "Unavailable: Samsung hardware and licensed SDK required" else runCatching {
        lists(policy(), "getAppPackageNamesAllBlackLists")
        "Native application-policy readback available; licence lifecycle needs Samsung validation"
    }.getOrElse { "Knox activation or permission required" }
    fun activate(licence: String) {
        check(supported); require(licence.isNotBlank())
        val cls = Class.forName("com.samsung.android.knox.license.KnoxEnterpriseLicenseManager")
        val manager = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
        cls.getMethod("activateLicense", String::class.java).invoke(manager, licence)
    }
    fun reconcile(target: Set<String>) {
        val api = policy()
        val allowMode = api.javaClass.getField("APPLICATION_INSTALLATION_MODE_ALLOW").getInt(null)
        check(api.javaClass.getMethod("getApplicationInstallationMode").invoke(api) == allowMode) { "Existing global Knox installation mode conflicts with default-allow policy" }
        val black = lists(api, "getAppPackageNamesAllBlackLists")
        val white = lists(api, "getAppPackageNamesAllWhiteLists")
        check(white.values.all { it.isEmpty() }) { "Existing Knox whitelist exceptions conflict with default-allow enforcement" }
        check(black.values.none { values -> values.any { it.contains('*') || it.contains('?') } }) { "Existing wildcard installation rules conflict with this policy" }
        check(black.filterKeys { it != context.packageName }.values.all { it.isEmpty() }) { "Another administrator has installation rules; resolve before applying" }
        val actual = black[context.packageName].orEmpty()
        for (id in target - actual) check(api.javaClass.getMethod("addAppPackageNameToBlackList", String::class.java).invoke(api, id) == true) { "Knox failed to block installation: $id" }
        for (id in actual - target) check(api.javaClass.getMethod("removeAppPackageNameFromBlackList", String::class.java).invoke(api, id) == true) { "Knox failed to unblock installation: $id" }
        check(lists(api, "getAppPackageNamesAllBlackLists")[context.packageName].orEmpty() == target) { "Knox installation blocklist readback differs from approved policy" }
        val enabled = api.javaClass.getMethod("getApplicationInstallationEnabled", String::class.java)
        check(target.all { enabled.invoke(api, it) == false }) { "Knox reports an approved blocked package as installable" }
        check(enabled.invoke(api, "be.anton.curatedphone.nonblockedprobe") == true && enabled.invoke(api, "com.android.chrome") == true) { "Knox default-allow installation readback failed" }
    }
}
class KnoxLicenseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A broadcast is a diagnostic hint, never evidence that package rules applied.
        be.anton.curatedphone.work.PolicyReconcileWorker.enqueue(context)
    }
}
