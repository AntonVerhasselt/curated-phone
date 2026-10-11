package be.anton.curatedphone.approval

import be.anton.curatedphone.data.Policy
import java.net.IDN

object PolicyValidator {
    val protectedPackages = setOf("be.anton.curatedphone", "com.android.chrome", "android", "com.android.systemui", "com.android.settings", "com.android.phone", "com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller", "com.sec.android.app.launcher", "com.samsung.android.dialer", "com.google.android.webview", "com.android.webview", "be.belfius.directmobile.android", "be.bmid.itsme", "com.whatsapp", "com.waze", "com.google.android.apps.maps", "com.google.android.projection.gearhead")
    fun domain(value: String): String {
        val host = IDN.toASCII(value.trim().lowercase().trimEnd('.'))
        require(host.length <= 253 && Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}").matches(host)) { "Enter a domain without a path or scheme" }
        return host
    }
    fun validate(policy: Policy, extraProtected: Set<String> = emptySet()) {
        require(policy.schemaVersion == 1 && policy.appPolicyMode == "blocklist") { "Unsupported policy schema" }
        require(policy.blockedAppPackageIds.size <= 500 && policy.blockedAppPackageIds.distinct().size == policy.blockedAppPackageIds.size)
        policy.blockedAppPackageIds.forEach { require(Regex("[a-zA-Z][\\w]*(?:\\.[a-zA-Z][\\w]*)+").matches(it) && it !in protectedPackages && it !in extraProtected) { "Protected or invalid package: $it" } }
        require(policy.categories.all { it in setOf("social", "streaming", "gambling", "adult") })
        policy.customDomains.forEach { require(domain(it) == it) }
        policy.categories.forEach { category -> require(policy.feeds.any { it.category == category }) { "A category feed is required for $category" } }
        policy.feeds.forEach { require(it.url.startsWith("https://") && it.licence.isNotBlank()) }
    }
}
