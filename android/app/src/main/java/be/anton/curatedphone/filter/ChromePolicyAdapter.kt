package be.anton.curatedphone.filter

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import be.anton.curatedphone.data.*

class ChromePolicyAdapter(private val dpm: DevicePolicyManager, private val admin: ComponentName, private val dao: PolicyDao, private val feeds: CategoryFeedProvider) {
    suspend fun apply(policy: Policy): Int {
        val domains = policy.customDomains.toMutableSet()
        for (source in policy.feeds.filter { it.category in policy.categories }) domains.addAll(feeds.load(source).domains)
        require(domains.size <= 1000) { "${domains.size} website rules exceed Chrome's 1,000-rule limit. A validated Chrome-only traffic filter is required; no entries were truncated." }
        val old = dpm.getApplicationRestrictions(admin, "com.android.chrome")
        if (dao.metadata("chromeOriginal") == null) dao.saveMetadata(MetadataEntity("chromeOriginal", encode(old)))
        val bundle = Bundle(old)
        bundle.putStringArray("URLBlocklist", domains.sorted().toTypedArray())
        // Existing exemptions must not bypass approved blocks.
        bundle.putStringArray("URLAllowlist", emptyArray())
        dpm.setApplicationRestrictions(admin, "com.android.chrome", bundle)
        val actual = dpm.getApplicationRestrictions(admin, "com.android.chrome")
        check(actual.getStringArray("URLBlocklist")?.toSet() == domains && actual.getStringArray("URLAllowlist")?.isEmpty() == true) { "Chrome managed-policy readback failed" }
        dao.saveMetadata(MetadataEntity("filterRuleCount", domains.size.toString()))
        return domains.size
    }
    suspend fun restore() {
        val original = dao.metadata("chromeOriginal") ?: return
        val bundle = decode(original)
        dpm.setApplicationRestrictions(admin, "com.android.chrome", bundle)
        check(encode(dpm.getApplicationRestrictions(admin, "com.android.chrome")) == encode(bundle)) { "Chrome policy cleanup readback failed" }
    }
    private fun encode(bundle: Bundle): String = Parcel.obtain().let { p -> try { p.writeBundle(bundle); Base64.encodeToString(p.marshall(), Base64.NO_WRAP) } finally { p.recycle() } }
    private fun decode(value: String): Bundle = Parcel.obtain().let { p -> try { val bytes = Base64.decode(value, Base64.NO_WRAP); p.unmarshall(bytes, 0, bytes.size); p.setDataPosition(0); p.readBundle(javaClass.classLoader) ?: Bundle() } finally { p.recycle() } }
}
