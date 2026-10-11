package be.anton.curatedphone
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import be.anton.curatedphone.data.*
import be.anton.curatedphone.network.CredentialVault
import be.anton.curatedphone.knox.KnoxPolicyAdapter
import kotlinx.coroutines.runBlocking
import androidx.room.Room

@RunWith(AndroidJUnit4::class)
class ControllerSmokeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun seedHasTwoHundredExactPackagesAndKeepsChromeAllowed() { val seed = context.assets.open("default-blocked-apps.json").bufferedReader().use { AppJson.decodeFromString<Seed>(it.readText()) }; assertEquals(200, seed.blockedAppPackageIds.size); assertFalse("com.android.chrome" in seed.blockedAppPackageIds) }
    @Test fun credentialRoundTripUsesKeystore() { val vault = CredentialVault(context); val previous = vault.token(); vault.saveToken("instrumentation-fixture-token"); assertEquals("instrumentation-fixture-token", vault.token()); if (previous != null) vault.saveToken(previous) else context.getSharedPreferences("device_credentials", 0).edit().clear().commit() }
    @Test fun roomPreservesApprovalJournalAndDraft() = runBlocking { val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build(); try { val dao = db.policies(); dao.saveDraft(DraftEntity(snapshot = "immutable-fixture")); dao.saveJournal(ApplyJournalEntity("operation-1", "signed-envelope", "payload", "applying", "suspend_apps")); assertEquals("immutable-fixture", dao.draft()!!.snapshot); assertEquals("suspend_apps", dao.unfinished().single().stage); dao.saveJournal(dao.unfinished().single().copy(status = "applied", acknowledged = true)); assertTrue(dao.unfinished().isEmpty()) } finally { db.close() } }
    @Test fun emulatorNeverClaimsKnoxSupport() { if (!android.os.Build.MANUFACTURER.equals("samsung", true)) { val knox = KnoxPolicyAdapter(context); assertFalse(knox.supported); assertTrue(knox.status().startsWith("Unavailable")) } }
}
