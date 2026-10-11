package be.anton.curatedphone.approval

import be.anton.curatedphone.data.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class ApprovalVerifier(private val keyId: String, private val publicKeyPem: String) {
    fun verifySignature(envelope: Envelope): ApprovalPayload {
        require(envelope.keyId == keyId && publicKeyPem.isNotBlank()) { "Approval key is unknown or not configured" }
        val der = Base64.getMimeDecoder().decode(publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").trim())
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        val valid = Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(envelope.payload.toByteArray(Charsets.UTF_8)); verify(Base64.getDecoder().decode(envelope.signature)) }
        require(valid) { "Approval signature is invalid" }
        val payload = AppJson.decodeFromString<ApprovalPayload>(envelope.payload)
        require(payload.schemaVersion == 1) { "Unsupported approval schema" }
        PolicyValidator.validate(payload.snapshot.policy)
        return payload
    }
    fun verify(envelope: Envelope, deviceId: String, request: RequestInfo, policyVersion: Long, guardianVersion: Long, now: Long = System.currentTimeMillis()): ApprovalPayload {
        val p = verifySignature(envelope)
        require(p.deviceId == deviceId && p.requestId == request.requestId && p.requestDigest == request.digest && p.purpose == request.purpose) { "Approval does not match this device and exact request" }
        require(p.snapshot == request.snapshot) { "Approval snapshot differs from submitted request" }
        require(p.basePolicyVersion == policyVersion && p.baseGuardianVersion == guardianVersion && p.targetPolicyVersion == policyVersion + 1) { "Stale approval or replay" }
        require(p.targetGuardianVersion == guardianVersion + (if (policyVersion == 0L || p.purpose.startsWith("replace_")) 1 else 0))
        require(p.issuedAt <= now + 300_000 && p.expiresAt > now) { "Approval is expired or not yet valid" }
        require(p.purpose in setOf("settings", "replace_primary", "replace_backup", "remove_management"))
        return p
    }
}
