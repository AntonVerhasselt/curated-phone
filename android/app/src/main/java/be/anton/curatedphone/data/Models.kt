package be.anton.curatedphone.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
@Serializable data class FeedSource(val id: String, val category: String, val url: String, val licence: String)
@Serializable data class Policy(val schemaVersion: Int = 1, val appPolicyMode: String = "blocklist", val blockedAppPackageIds: List<String> = emptyList(), val categories: List<String> = emptyList(), val feeds: List<FeedSource> = emptyList(), val customDomains: List<String> = emptyList())
@Serializable data class Snapshot(val policy: Policy, val primaryEmail: String, val backupEmail: String)
@Serializable data class Envelope(val payload: String, val signature: String, val keyId: String)
@Serializable data class ApprovalPayload(val schemaVersion: Int, val deviceId: String, val requestId: String, val approvalId: String, val purpose: String, val requestDigest: String, val basePolicyVersion: Long, val baseGuardianVersion: Long, val targetPolicyVersion: Long, val targetGuardianVersion: Long, val snapshot: Snapshot, val issuedAt: Long, val expiresAt: Long)
@Serializable data class RequestInfo(val requestId: String, val purpose: String, val recovery: Boolean = false, val snapshot: Snapshot, val diff: List<String>, val digest: String, val basePolicyVersion: Long, val baseGuardianVersion: Long, val status: String, val createdAt: Long, val failure: String? = null)
@Serializable data class Challenge(val channel: String, val generation: Int, val email: String, val expiresAt: Long, val attempts: Int, val verified: Boolean, val delivery: String, val resendAt: Long)
@Serializable data class FeedStatus(val id: String, val category: String, val url: String, val licence: String, val version: String, val checksum: String, val fetchedAt: Long, val fetchStatus: String)
@Serializable data class Guardian(val role: String, val email: String, val verified: Boolean)
@Serializable data class DeviceState(val deviceId: String, val label: String, val status: String, val approvedPolicyVersion: Long, val appliedPolicyVersion: Long, val guardianVersion: Long, val snapshot: Snapshot? = null, val guardians: List<Guardian> = emptyList(), val pending: RequestInfo? = null, val challenges: List<Challenge> = emptyList(), val approval: Envelope? = null, val history: List<RequestInfo> = emptyList(), val feeds: List<FeedStatus> = emptyList())
@Serializable data class PairResult(val deviceId: String, val primaryEmail: String, val backupEmail: String, val token: String)
@Serializable data class VerifyResult(val ok: Boolean, val authorized: Boolean = false, val error: String? = null, val approval: Envelope? = null)
@Serializable data class SubmitResult(val requestId: String)
@Serializable data class Seed(val seedVersion: Int, val appPolicyMode: String, val blockedAppPackageIds: List<String>)
data class InstalledApp(val name: String, val packageId: String, val protected: Boolean)
