package be.anton.curatedphone
import be.anton.curatedphone.approval.*
import be.anton.curatedphone.data.*
import kotlinx.serialization.Serializable
import org.junit.Assert.*
import org.junit.Test

class ApprovalVerifierTest {
    @Serializable data class Fixture(val publicKey: String, val envelope: Envelope)
    private val fixture = AppJson.decodeFromString<Fixture>(javaClass.classLoader!!.getResource("node-signature.json")!!.readText())
    private val verifier = ApprovalVerifier("fixture-p256-1", fixture.publicKey)
    private val payload get() = verifier.verifySignature(fixture.envelope)
    private val request get() = RequestInfo("fixture-request", "settings", snapshot = payload.snapshot, diff = emptyList(), digest = "fixture-digest", basePolicyVersion = 0, baseGuardianVersion = 0, status = "authorized", createdAt = 1700000000000)
    private fun reject(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } }
    @Test fun verifiesActualNodeSignatureWithoutReserializingBytes() { assertEquals("fixture-approval", verifier.verify(fixture.envelope, "fixture-device", request, 0, 0, 1700000001000).approvalId) }
    @Test fun rejectsTamperedSignedPayload() { reject { verifier.verifySignature(fixture.envelope.copy(payload = fixture.envelope.payload.replace("instagram", "linkedin"))) } }
    @Test fun rejectsWrongDevice() { reject { verifier.verify(fixture.envelope, "other-device", request, 0, 0, 1700000001000) } }
    @Test fun rejectsUnknownSigningKey() { reject { verifier.verifySignature(fixture.envelope.copy(keyId = "unknown")) } }
    @Test fun rejectsOldVersionsAndReplay() { reject { verifier.verify(fixture.envelope, "fixture-device", request, 1, 1, 1700000001000) } }
    @Test fun rejectsChangedDigestOrPurpose() { reject { verifier.verify(fixture.envelope, "fixture-device", request.copy(digest = "changed"), 0, 0, 1700000001000) }; reject { verifier.verify(fixture.envelope, "fixture-device", request.copy(purpose = "remove_management"), 0, 0, 1700000001000) } }
    @Test fun rejectsExpiredApprovals() { reject { verifier.verify(fixture.envelope, "fixture-device", request, 0, 0, 1700604800001) } }
    @Test fun rejectsLocallyEditedSnapshot() { reject { verifier.verify(fixture.envelope, "fixture-device", request.copy(snapshot = request.snapshot.copy(policy = request.snapshot.policy.copy(blockedAppPackageIds = emptyList()))), 0, 0, 1700000001000) } }
    @Test fun rejectsUnsupportedSchema() { reject { PolicyValidator.validate(Policy(schemaVersion = 2)) } }
    @Test fun rejectsEssentialPackagesAndMalformedDomains() { reject { PolicyValidator.validate(Policy(blockedAppPackageIds = listOf("com.android.chrome"))) }; reject { PolicyValidator.domain("https://example.org/video") }; assertEquals("example.org", PolicyValidator.domain("Example.org.")) }
}
