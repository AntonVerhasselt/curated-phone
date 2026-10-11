import { internalMutation, internalQuery, type MutationCtx, type QueryCtx } from './_generated/server';
import { v } from 'convex/values';
import { internal } from './_generated/api';
import { snapshot, purpose } from './schema';
import { canonical, diff, normalizeSnapshot, validatePurpose } from './lib/policy';
import type { Doc } from './_generated/dataModel';
async function device(ctx: QueryCtx | MutationCtx, tokenDigest: string, allowRetired = false) {
  const d = await ctx.db.query('devices').withIndex('by_token', q => q.eq('tokenDigest', tokenDigest)).unique();
  if (!d || (d.status === 'retired' && !allowRetired)) throw new Error('Unauthorized');
  return d;
}
async function request(ctx: QueryCtx | MutationCtx, d: Doc<'devices'>, requestId: string) {
  const r = await ctx.db.query('changeRequests').withIndex('by_request', q => q.eq('requestId', requestId)).unique();
  if (!r || r.deviceId !== d.deviceId) throw new Error('Request not found');
  return r;
}
async function current(ctx: QueryCtx | MutationCtx, d: Doc<'devices'>) {
  return (await ctx.db.query('policies').withIndex('by_device_version', q => q.eq('deviceId', d.deviceId).eq('version', d.approvedPolicyVersion)).unique())?.snapshot ?? null;
}
async function audit(ctx: MutationCtx, deviceId: string, type: string, requestId?: string, result?: string) {
  await ctx.db.insert('auditEvents', { deviceId, type, requestId, result, at: Date.now() });
}
export const createPairing = internalMutation({ args: { digest: v.string(), label: v.string(), primaryEmail: v.string(), backupEmail: v.string() }, handler: async (ctx, a) => {
  await ctx.db.insert('pairingCodes', { ...a, expiresAt: Date.now() + 10 * 60_000 });
} });
export const pair = internalMutation({ args: { digest: v.string(), tokenDigest: v.string(), deviceId: v.string(), platform: v.string() }, handler: async (ctx, a) => {
  const p = await ctx.db.query('pairingCodes').withIndex('by_digest', q => q.eq('digest', a.digest)).unique();
  if (!p || p.consumedAt || p.expiresAt <= Date.now()) throw new Error('Invalid pairing code');
  await ctx.db.patch(p._id, { consumedAt: Date.now() });
  await ctx.db.insert('devices', { deviceId: a.deviceId, tokenDigest: a.tokenDigest, label: p.label, platform: a.platform.slice(0, 200), status: 'onboarding', approvedPolicyVersion: 0, appliedPolicyVersion: 0, guardianVersion: 0, lastSeenAt: Date.now(), verifyAttempts: [], sends: [] });
  for (const role of ['primary', 'recovery'] as const) await ctx.db.insert('guardians', { deviceId: a.deviceId, role, email: role === 'primary' ? p.primaryEmail : p.backupEmail, guardianVersion: 0 });
  await audit(ctx, a.deviceId, 'paired');
  return { deviceId: a.deviceId, primaryEmail: p.primaryEmail, backupEmail: p.backupEmail };
} });
export const state = internalQuery({ args: { tokenDigest: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest, true);
  const requests = await ctx.db.query('changeRequests').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).order('desc').take(50);
  const pending = requests.find(r => ['pending', 'authorized', 'failed'].includes(r.status));
  const challenges = pending ? await ctx.db.query('otpChallenges').withIndex('by_request', q => q.eq('requestId', pending.requestId)).collect() : [];
  const approvals = pending ? await ctx.db.query('approvals').withIndex('by_request', q => q.eq('requestId', pending.requestId)).unique() : null;
  const guardians = await ctx.db.query('guardians').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect();
  return { deviceId: d.deviceId, label: d.label, status: d.status, approvedPolicyVersion: d.approvedPolicyVersion, appliedPolicyVersion: d.appliedPolicyVersion, guardianVersion: d.guardianVersion, snapshot: await current(ctx, d), guardians: guardians.map(g => ({ role: g.role, email: g.email, verified: !!g.verifiedAt })), pending: pending ? publicRequest(pending) : null, challenges: challenges.map(c => ({ channel: c.channel, generation: c.generation, email: c.email, expiresAt: c.expiresAt, attempts: c.attempts, verified: !!c.consumedAt, delivery: c.delivery, resendAt: c.sentAt + 60_000 })), approval: approvals ? envelope(approvals) : null, history: requests.map(publicRequest), feeds: (await ctx.db.query('categoryFeeds').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect()).map(f => ({ id: f.id, category: f.category, url: f.url, licence: f.licence, version: f.version, checksum: f.checksum, fetchedAt: f.fetchedAt, fetchStatus: f.fetchStatus })) };
} });
function envelope(a: Doc<'approvals'>) { return { payload: a.payload, signature: a.signature, keyId: a.keyId }; }
function publicRequest(r: Doc<'changeRequests'>) { return { requestId: r.requestId, purpose: r.purpose, recovery: r.recovery, snapshot: r.snapshot, diff: r.diff, digest: r.digest, basePolicyVersion: r.basePolicyVersion, baseGuardianVersion: r.baseGuardianVersion, status: r.status, createdAt: r.createdAt, failure: r.failure }; }
export const submit = internalMutation({ args: { tokenDigest: v.string(), requestId: v.string(), purpose, recovery: v.boolean(), snapshot, digest: v.string(), basePolicyVersion: v.number(), baseGuardianVersion: v.number() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), before = await current(ctx, d), next = normalizeSnapshot(a.snapshot);
  if (d.approvedPolicyVersion !== d.appliedPolicyVersion) throw new Error('Finish applying the previous approval first');
  if (a.basePolicyVersion !== d.approvedPolicyVersion || a.baseGuardianVersion !== d.guardianVersion) throw new Error('Stale versions: sync and review again');
  validatePurpose(a.purpose, a.recovery, before, next);
  const guardians = await ctx.db.query('guardians').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect();
  if (!before && (next.primaryEmail !== guardians.find(g => g.role === 'primary')?.email || next.backupEmail !== guardians.find(g => g.role === 'recovery')?.email)) throw new Error('Initial guardians must match pairing');
  const existing = await ctx.db.query('changeRequests').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect();
  if (existing.some(r => r.status === 'authorized' || r.status === 'failed')) throw new Error('An approved operation is still applying');
  for (const r of existing.filter(r => r.status === 'pending')) { await ctx.db.patch(r._id, { status: 'cancelled' }); await audit(ctx, d.deviceId, 'superseded', r.requestId); }
  const changes = a.purpose === 'remove_management' ? ['End all app restrictions, website filtering, and device management'] : diff(before, next);
  if (!changes.length) throw new Error('No changes to approve');
  if (canonical(next).length > 40_000 || changes.join('\n').length > 40_000) throw new Error('Request is too large');
  await ctx.db.insert('changeRequests', { deviceId: d.deviceId, requestId: a.requestId, purpose: a.purpose, recovery: a.recovery, snapshot: next, digest: a.digest, diff: changes, status: 'pending', createdAt: Date.now(), basePolicyVersion: d.approvedPolicyVersion, baseGuardianVersion: d.guardianVersion });
  await audit(ctx, d.deviceId, 'submitted', a.requestId);
  const channels = [{ channel: 'authorize', email: a.recovery ? (before?.backupEmail ?? next.backupEmail) : (before?.primaryEmail ?? next.primaryEmail), role: a.recovery ? 'recovery' : 'primary' }];
  if (!before || a.purpose === 'replace_backup') channels.push({ channel: 'verify_backup', email: next.backupEmail, role: 'new_inbox' });
  if (a.purpose === 'replace_primary') channels.push({ channel: 'verify_primary', email: next.primaryEmail, role: 'new_inbox' });
  return { requestId: a.requestId, channels };
} });
export const challengeContext = internalQuery({ args: { tokenDigest: v.string(), requestId: v.string(), channel: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), r = await request(ctx, d, a.requestId);
  if (r.status !== 'pending') throw new Error('Request is not pending');
  const before = await current(ctx, d);
  let recipient: string, role: string;
  if (a.channel === 'authorize') { recipient = r.recovery ? (before?.backupEmail ?? r.snapshot.backupEmail) : (before?.primaryEmail ?? r.snapshot.primaryEmail); role = r.recovery ? 'recovery' : 'primary'; }
  else if (a.channel === 'verify_backup' && (r.basePolicyVersion === 0 || r.purpose === 'replace_backup')) { recipient = r.snapshot.backupEmail; role = 'new_inbox'; }
  else if (a.channel === 'verify_primary' && r.purpose === 'replace_primary') { recipient = r.snapshot.primaryEmail; role = 'new_inbox'; }
  else throw new Error('Invalid challenge channel');
  const c = (await ctx.db.query('otpChallenges').withIndex('by_request', q => q.eq('requestId', r.requestId)).collect()).find(c => c.channel === a.channel);
  return { request: publicRequest(r), label: d.label, email: recipient, role, generation: (c?.generation ?? 0) + 1 };
} });
export const issue = internalMutation({ args: { tokenDigest: v.string(), requestId: v.string(), channel: v.string(), generation: v.number(), email: v.string(), role: v.string(), codeHmac: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), r = await request(ctx, d, a.requestId), now = Date.now();
  if (r.status !== 'pending') throw new Error('Request is not pending');
  const prior = (await ctx.db.query('otpChallenges').withIndex('by_request', q => q.eq('requestId', r.requestId)).collect()).find(c => c.channel === a.channel);
  if (prior && (prior.consumedAt || prior.generation + 1 !== a.generation || now - prior.sentAt < 60_000)) throw new Error('Challenge already verified or resend is too soon');
  if (!prior && a.generation !== 1) throw new Error('Challenge generation conflict');
  const sends = d.sends.filter(s => s.at > now - 3_600_000);
  if (sends.filter(s => s.email === a.email).length >= 5) throw new Error('Hourly email limit reached');
  await ctx.db.patch(d._id, { sends: [...sends, { at: now, email: a.email }], lastSeenAt: now });
  const data = { requestId: r.requestId, channel: a.channel, recipientRole: a.role, email: a.email, generation: a.generation, codeHmac: a.codeHmac, expiresAt: now + 600_000, attempts: 0, sentAt: now, delivery: 'queued' };
  if (prior) { await ctx.db.patch(prior._id, { ...data, emailJobId: undefined }); return prior._id; }
  return await ctx.db.insert('otpChallenges', data);
} });
export const emailResult = internalMutation({ args: { id: v.id('otpChallenges'), generation: v.number(), delivery: v.string(), emailJobId: v.optional(v.string()) }, handler: async (ctx, a) => {
  const c = await ctx.db.get(a.id);
  if (c?.generation === a.generation) await ctx.db.patch(a.id, { delivery: a.delivery, emailJobId: a.emailJobId });
} });
export const verify = internalMutation({ args: { tokenDigest: v.string(), requestId: v.string(), channel: v.string(), generation: v.number(), codeHmac: v.string(), approvalId: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), r = await request(ctx, d, a.requestId), now = Date.now();
  if (['authorized', 'failed', 'applied'].includes(r.status)) return { ok: true, authorized: true };
  if (r.status !== 'pending' || d.approvedPolicyVersion !== r.basePolicyVersion || d.guardianVersion !== r.baseGuardianVersion) return { ok: false, error: 'Stale or inactive request' };
  const attempts = d.verifyAttempts.filter(t => t > now - 3_600_000);
  if (attempts.length >= 20) return { ok: false, error: 'Hourly verification limit reached' };
  await ctx.db.patch(d._id, { verifyAttempts: [...attempts, now], lastSeenAt: now });
  const challenges = await ctx.db.query('otpChallenges').withIndex('by_request', q => q.eq('requestId', r.requestId)).collect();
  const c = challenges.find(c => c.channel === a.channel);
  if (!c || c.generation !== a.generation || c.expiresAt <= now || c.attempts >= 5) return { ok: false, error: 'Expired, locked, or previous-generation code' };
  if (!c.consumedAt && c.codeHmac !== a.codeHmac) { await ctx.db.patch(c._id, { attempts: c.attempts + 1 }); await audit(ctx, d.deviceId, 'verification_failed', r.requestId); return { ok: false, error: 'Incorrect code' }; }
  if (!c.consumedAt) await ctx.db.patch(c._id, { consumedAt: now });
  const required = ['authorize'];
  if (r.basePolicyVersion === 0 || r.purpose === 'replace_backup') required.push('verify_backup');
  if (r.purpose === 'replace_primary') required.push('verify_primary');
  if (!required.every(ch => ch === a.channel || challenges.some(c => c.channel === ch && c.consumedAt))) return { ok: true, authorized: false };
  const nextPolicy = d.approvedPolicyVersion + 1;
  const nextGuardian = d.guardianVersion + (r.basePolicyVersion === 0 || r.purpose.startsWith('replace_') ? 1 : 0);
  await ctx.db.patch(r._id, { status: 'authorized', approvalId: a.approvalId, issuedAt: now, expiresAt: now + 7 * 86_400_000, targetPolicyVersion: nextPolicy, targetGuardianVersion: nextGuardian });
  await ctx.db.insert('policies', { deviceId: d.deviceId, version: nextPolicy, snapshot: r.snapshot, digest: r.digest });
  await ctx.db.patch(d._id, { approvedPolicyVersion: nextPolicy, guardianVersion: nextGuardian });
  const guardians = await ctx.db.query('guardians').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect();
  for (const g of guardians) { const address = g.role === 'primary' ? r.snapshot.primaryEmail : r.snapshot.backupEmail; await ctx.db.patch(g._id, { email: address, verifiedAt: address === g.email ? (g.verifiedAt ?? now) : now, guardianVersion: nextGuardian }); }
  if (r.purpose.startsWith('replace_')) {
    const before = await ctx.db.query('policies').withIndex('by_device_version', q => q.eq('deviceId', d.deviceId).eq('version', r.basePolicyVersion)).unique();
    await ctx.scheduler.runAfter(0, internal.notifications.guardianChanged, { emails: [before!.snapshot.primaryEmail, before!.snapshot.backupEmail, r.snapshot.primaryEmail, r.snapshot.backupEmail], requestId: r.requestId, primaryEmail: r.snapshot.primaryEmail, backupEmail: r.snapshot.backupEmail });
  }
  await audit(ctx, d.deviceId, 'authorized', r.requestId);
  return { ok: true, authorized: true };
} });
export const signingContext = internalQuery({ args: { tokenDigest: v.string(), requestId: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), r = await request(ctx, d, a.requestId);
  if (!r.approvalId) throw new Error('Request is not authorized');
  const existing = await ctx.db.query('approvals').withIndex('by_request', q => q.eq('requestId', r.requestId)).unique();
  return { existing: existing ? envelope(existing) : null, payload: { schemaVersion: 1, deviceId: d.deviceId, requestId: r.requestId, approvalId: r.approvalId, purpose: r.purpose, requestDigest: r.digest, basePolicyVersion: r.basePolicyVersion, baseGuardianVersion: r.baseGuardianVersion, targetPolicyVersion: r.targetPolicyVersion!, targetGuardianVersion: r.targetGuardianVersion!, snapshot: r.snapshot, issuedAt: r.issuedAt!, expiresAt: r.expiresAt! } };
} });
export const saveApproval = internalMutation({ args: { tokenDigest: v.string(), requestId: v.string(), payload: v.string(), signature: v.string(), keyId: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), r = await request(ctx, d, a.requestId);
  if (!r.approvalId) throw new Error('Request is not authorized');
  const existing = await ctx.db.query('approvals').withIndex('by_request', q => q.eq('requestId', r.requestId)).unique();
  if (existing) return envelope(existing);
  await ctx.db.insert('approvals', { deviceId: d.deviceId, requestId: r.requestId, approvalId: r.approvalId, payload: a.payload, signature: a.signature, keyId: a.keyId, targetVersion: r.targetPolicyVersion! });
  return { payload: a.payload, signature: a.signature, keyId: a.keyId };
} });
export const cancel = internalMutation({ args: { tokenDigest: v.string(), requestId: v.string() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), r = await request(ctx, d, a.requestId);
  if (r.status !== 'pending') throw new Error('Authorization cannot be cancelled');
  await ctx.db.patch(r._id, { status: 'cancelled' }); await audit(ctx, d.deviceId, 'cancelled', r.requestId);
  return { ok: true };
} });
export const applied = internalMutation({ args: { tokenDigest: v.string(), requestId: v.string(), approvalId: v.string(), version: v.number(), success: v.boolean(), failure: v.optional(v.string()), ownershipReleased: v.optional(v.boolean()) }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest, true), r = await request(ctx, d, a.requestId);
  if (!['authorized', 'failed', 'applied'].includes(r.status) || r.approvalId !== a.approvalId || r.targetPolicyVersion !== a.version) throw new Error('Application result does not match approval');
  if (r.status === 'applied') return { ok: true };
  if (a.success && r.purpose === 'remove_management' && !a.ownershipReleased) throw new Error('Ownership release must be confirmed');
  await ctx.db.patch(r._id, { status: a.success ? 'applied' : 'failed', failure: a.success ? undefined : (a.failure ?? 'Native application failed').slice(0, 1000) });
  if (a.success) {
    await ctx.db.patch(d._id, { appliedPolicyVersion: a.version, status: r.purpose === 'remove_management' ? 'retired' : 'active' });
    const approval = await ctx.db.query('approvals').withIndex('by_request', q => q.eq('requestId', r.requestId)).unique();
    if (approval) await ctx.db.patch(approval._id, { appliedAt: Date.now() });
  }
  await audit(ctx, d.deviceId, a.success ? 'applied' : 'apply_failed', r.requestId);
  return { ok: true };
} });

export const feedStatus = internalMutation({ args: { tokenDigest: v.string(), feedId: v.string(), url: v.string(), checksum: v.string(), fetchedAt: v.number() }, handler: async (ctx, a) => {
  const d = await device(ctx, a.tokenDigest), snapshot = await current(ctx, d);
  const source = snapshot?.policy.feeds.find(f => f.id === a.feedId);
  if (!source || source.url !== a.url || !/^[a-f0-9]{64}$/.test(a.checksum) || a.fetchedAt < 0 || a.fetchedAt > Date.now() + 300_000) throw new Error('Feed metadata must match an approved source');
  const prior = await ctx.db.query('categoryFeeds').withIndex('by_device_feed', q => q.eq('deviceId', d.deviceId).eq('id', a.feedId)).unique();
  const data = { deviceId: d.deviceId, ...source, version: a.checksum, checksum: a.checksum, fetchedAt: a.fetchedAt, fetchStatus: 'cached' };
  if (prior) await ctx.db.patch(prior._id, data); else await ctx.db.insert('categoryFeeds', data);
  return { ok: true };
} });
