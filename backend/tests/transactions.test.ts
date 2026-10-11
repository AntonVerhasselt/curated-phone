import { describe, it, expect, vi, afterEach } from 'vitest';
import { convexTest } from 'convex-test';
import schema from '../convex/schema';
import { internal } from '../convex/_generated/api';
import { base } from './fixtures';
const modules = import.meta.glob('../convex/**/*.ts');
async function setup() {
  const t = convexTest(schema, modules);
  await t.mutation(internal.store.createPairing, { digest: 'pair', label: 'Test Samsung', primaryEmail: base.primaryEmail, backupEmail: base.backupEmail });
  await t.mutation(internal.store.pair, { digest: 'pair', tokenDigest: 'token', deviceId: 'device-1', platform: 'test' });
  await t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'request-1', purpose: 'settings', recovery: false, snapshot: base, digest: 'content', basePolicyVersion: 0, baseGuardianVersion: 0 });
  for (const channel of ['authorize', 'verify_backup']) await t.mutation(internal.store.issue, { tokenDigest: 'token', requestId: 'request-1', channel, generation: 1, email: channel === 'authorize' ? base.primaryEmail : base.backupEmail, role: channel === 'authorize' ? 'primary' : 'new_inbox', codeHmac: channel + '-hash' });
  return t;
}
const verify = (channel: string, extra = {}) => ({ tokenDigest: 'token', requestId: 'request-1', channel, generation: 1, codeHmac: channel + '-hash', approvalId: 'approval-1', ...extra });
afterEach(() => vi.useRealTimers());
describe('Atomic OTP and device isolation', () => {
  it('feed cache telemetry cannot add an unapproved source', async () => { const t = await setup(); await expect(t.mutation(internal.store.feedStatus, { tokenDigest: 'token', feedId: 'unapproved', url: 'https://example.org/domains.txt', checksum: 'a'.repeat(64), fetchedAt: Date.now() })).rejects.toThrow('approved source'); });
  it('consumes pairing exactly once', async () => { const t = await setup(); await expect(t.mutation(internal.store.pair, { digest: 'pair', tokenDigest: 'other', deviceId: 'device-2', platform: 'test' })).rejects.toThrow('Invalid pairing'); });
  it('rejects missing or different device credentials', async () => {
    const t = await setup(); await expect(t.query(internal.store.state, { tokenDigest: 'wrong' })).rejects.toThrow('Unauthorized');
    await t.mutation(internal.store.createPairing, { digest: 'pair-2', label: 'Other phone', primaryEmail: base.primaryEmail, backupEmail: base.backupEmail });
    await t.mutation(internal.store.pair, { digest: 'pair-2', tokenDigest: 'token-2', deviceId: 'device-2', platform: 'test' });
    await expect(t.mutation(internal.store.verify, verify('authorize', { tokenDigest: 'token-2' }))).rejects.toThrow('Request not found');
  });
  it('persists failed attempts instead of rolling back on an exception', async () => { const t = await setup(); for (let i = 0; i < 5; i++) expect((await t.mutation(internal.store.verify, verify('authorize', { codeHmac: 'wrong' }))).ok).toBe(false); const state = await t.query(internal.store.state, { tokenDigest: 'token' }); expect(state.challenges.find(c => c.channel === 'authorize')?.attempts).toBe(5); expect((await t.mutation(internal.store.verify, verify('authorize'))).ok).toBe(false); });
  it('requires both guardian inboxes in initial setup', async () => { const t = await setup(); expect(await t.mutation(internal.store.verify, verify('authorize'))).toEqual({ ok: true, authorized: false }); expect(await t.mutation(internal.store.verify, verify('verify_backup'))).toEqual({ ok: true, authorized: true }); });
  it('concurrent valid checks create one authorization', async () => {
    const t = await setup(); await t.mutation(internal.store.verify, verify('verify_backup'));
    const results = await Promise.all([t.mutation(internal.store.verify, verify('authorize', { approvalId: 'first' })), t.mutation(internal.store.verify, verify('authorize', { approvalId: 'second' }))]);
    expect(results.every(r => r.ok)).toBe(true);
    const records = await t.run(async ctx => ctx.db.query('policies').collect()); expect(records).toHaveLength(1);
    const state = await t.query(internal.store.state, { tokenDigest: 'token' }); expect(state.approvedPolicyVersion).toBe(1); expect(state.appliedPolicyVersion).toBe(0);
  });
  it('expired and previous-generation challenges cannot approve', async () => {
    vi.useFakeTimers(); const t = await setup(); vi.advanceTimersByTime(60_001);
    await t.mutation(internal.store.issue, { tokenDigest: 'token', requestId: 'request-1', channel: 'authorize', generation: 2, email: base.primaryEmail, role: 'primary', codeHmac: 'new-hash' });
    expect((await t.mutation(internal.store.verify, verify('authorize'))).ok).toBe(false);
    vi.advanceTimersByTime(600_001); expect((await t.mutation(internal.store.verify, verify('authorize', { generation: 2, codeHmac: 'new-hash' }))).ok).toBe(false);
  });
  it('resends preserve a rolling verification budget', async () => { vi.useFakeTimers(); const t = await setup(); for (let generation = 1; generation <= 4; generation++) { for (let n = 0; n < 5; n++) await t.mutation(internal.store.verify, verify('authorize', { generation, codeHmac: 'wrong' })); vi.advanceTimersByTime(60_001); if (generation < 4) await t.mutation(internal.store.issue, { tokenDigest: 'token', requestId: 'request-1', channel: 'authorize', generation: generation + 1, email: base.primaryEmail, role: 'primary', codeHmac: 'authorize-hash' }); } expect((await t.mutation(internal.store.verify, verify('verify_backup'))).error).toBe('Hourly verification limit reached'); });
  it('delayed older email results do not overwrite a new generation', async () => { vi.useFakeTimers(); const t = await setup(); const id = await t.run(async ctx => (await ctx.db.query('otpChallenges').collect()).find(c => c.channel === 'authorize')!._id); vi.advanceTimersByTime(60_001); await t.mutation(internal.store.issue, { tokenDigest: 'token', requestId: 'request-1', channel: 'authorize', generation: 2, email: base.primaryEmail, role: 'primary', codeHmac: 'new' }); await t.mutation(internal.store.emailResult, { id, generation: 1, delivery: 'failed' }); expect((await t.query(internal.store.state, { tokenDigest: 'token' })).challenges.find(c => c.channel === 'authorize')?.delivery).toBe('queued'); });
  it('approved operations cannot be cancelled or overlapped', async () => { const t = await setup(); await t.mutation(internal.store.verify, verify('authorize')); await t.mutation(internal.store.verify, verify('verify_backup')); await expect(t.mutation(internal.store.cancel, { tokenDigest: 'token', requestId: 'request-1' })).rejects.toThrow('cannot be cancelled'); await expect(t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'next', purpose: 'settings', recovery: false, snapshot: base, digest: 'next', basePolicyVersion: 1, baseGuardianVersion: 1 })).rejects.toThrow('Finish applying'); });
  it('failure acknowledges authorization without declaring application success', async () => { const t = await setup(); await t.mutation(internal.store.verify, verify('authorize')); await t.mutation(internal.store.verify, verify('verify_backup')); await t.mutation(internal.store.applied, { tokenDigest: 'token', requestId: 'request-1', approvalId: 'approval-1', version: 1, success: false, failure: 'Knox unavailable' }); const state = await t.query(internal.store.state, { tokenDigest: 'token' }); expect(state.pending?.status).toBe('failed'); expect(state.appliedPolicyVersion).toBe(0); await t.mutation(internal.store.applied, { tokenDigest: 'token', requestId: 'request-1', approvalId: 'approval-1', version: 1, success: true }); expect((await t.query(internal.store.state, { tokenDigest: 'token' })).appliedPolicyVersion).toBe(1); });
  it('new submissions invalidate pending batches and keep exact snapshots', async () => { const t = await setup(); const next = { ...base, policy: { ...base.policy, blockedAppPackageIds: [] } }; await t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'request-2', purpose: 'settings', recovery: false, snapshot: next, digest: 'new', basePolicyVersion: 0, baseGuardianVersion: 0 }); const state = await t.query(internal.store.state, { tokenDigest: 'token' }); expect(state.history.find(r => r.requestId === 'request-1')?.status).toBe('cancelled'); expect(state.pending?.snapshot.policy.blockedAppPackageIds).toEqual([]); expect((await t.mutation(internal.store.verify, verify('authorize'))).ok).toBe(false); });
  it('guardian replacement requires current-primary authorization and separate new-inbox proof', async () => {
    const t = await setup(); await t.mutation(internal.store.verify, verify('authorize')); await t.mutation(internal.store.verify, verify('verify_backup'));
    await t.mutation(internal.store.applied, { tokenDigest: 'token', requestId: 'request-1', approvalId: 'approval-1', version: 1, success: true });
    const next = { ...base, primaryEmail: 'new-primary@example.org' };
    await t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'replace', purpose: 'replace_primary', recovery: false, snapshot: next, digest: 'replacement', basePolicyVersion: 1, baseGuardianVersion: 1 });
    const auth = await t.query(internal.store.challengeContext, { tokenDigest: 'token', requestId: 'replace', channel: 'authorize' });
    expect(auth.email).toBe(base.primaryEmail); expect(auth.role).toBe('primary');
    for (const channel of ['authorize', 'verify_primary']) await t.mutation(internal.store.issue, { tokenDigest: 'token', requestId: 'replace', channel, generation: 1, email: channel === 'authorize' ? base.primaryEmail : next.primaryEmail, role: channel === 'authorize' ? 'primary' : 'new_inbox', codeHmac: channel });
    expect((await t.mutation(internal.store.verify, { ...verify('authorize'), requestId: 'replace', codeHmac: 'authorize', approvalId: 'replacement' })).authorized).toBe(false);
    expect((await t.query(internal.store.state, { tokenDigest: 'token' })).guardians.find(g => g.role === 'primary')?.email).toBe(base.primaryEmail);
    expect((await t.mutation(internal.store.verify, { ...verify('verify_primary'), requestId: 'replace', codeHmac: 'verify_primary', approvalId: 'replacement' })).authorized).toBe(true);
    expect((await t.query(internal.store.state, { tokenDigest: 'token' })).guardianVersion).toBe(2);
  });
  it('removal requires primary authorization and crash-safe ownership-release acknowledgement', async () => {
    const t = await setup(); await t.mutation(internal.store.verify, verify('authorize')); await t.mutation(internal.store.verify, verify('verify_backup'));
    await t.mutation(internal.store.applied, { tokenDigest: 'token', requestId: 'request-1', approvalId: 'approval-1', version: 1, success: true });
    await expect(t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'bad', purpose: 'remove_management', recovery: true, snapshot: base, digest: 'bad', basePolicyVersion: 1, baseGuardianVersion: 1 })).rejects.toThrow('Recovery may only');
    await t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'remove', purpose: 'remove_management', recovery: false, snapshot: base, digest: 'remove', basePolicyVersion: 1, baseGuardianVersion: 1 });
    await t.mutation(internal.store.issue, { tokenDigest: 'token', requestId: 'remove', channel: 'authorize', generation: 1, email: base.primaryEmail, role: 'primary', codeHmac: 'remove' });
    await t.mutation(internal.store.verify, { ...verify('authorize'), requestId: 'remove', codeHmac: 'remove', approvalId: 'remove-approval' });
    const result = { tokenDigest: 'token', requestId: 'remove', approvalId: 'remove-approval', version: 2, success: true, ownershipReleased: true };
    await expect(t.mutation(internal.store.applied, { ...result, ownershipReleased: false })).rejects.toThrow('Ownership release');
    await t.mutation(internal.store.applied, result); expect(await t.mutation(internal.store.applied, result)).toEqual({ ok: true });
    expect((await t.query(internal.store.state, { tokenDigest: 'token' })).status).toBe('retired');
    await expect(t.mutation(internal.store.submit, { tokenDigest: 'token', requestId: 'after-retire', purpose: 'settings', recovery: false, snapshot: base, digest: 'after', basePolicyVersion: 2, baseGuardianVersion: 1 })).rejects.toThrow('Unauthorized');
  });
});
