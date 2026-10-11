"use node";
import { randomBytes, randomInt, randomUUID, createHmac, createHash, sign } from 'node:crypto';
import { internalAction } from './_generated/server';
import { internal, components } from './_generated/api';
import { v } from 'convex/values';
import { Resend } from '@convex-dev/resend';
import { canonical, email, normalizeSnapshot } from './lib/policy';
import type { ActionCtx } from './_generated/server';
function env(name: string) { const value = process.env[name]; if (!value) throw new Error(`Backend configuration missing: ${name}`); return value; }
export function hmac(secret: string, ...parts: string[]) { return createHmac('sha256', secret).update(parts.join('\0')).digest('hex'); }
const tokenDigest = (token: string) => hmac(env('DEVICE_TOKEN_HMAC_SECRET'), token);
const codeDigest = (requestId: string, channel: string, generation: number, role: string, code: string) => hmac(env('OTP_HMAC_SECRET'), requestId, String(generation), channel, role, code);
const mail = () => new Resend(components.resend, { testMode: process.env.RESEND_TEST_MODE !== 'false', onEmailEvent: internal.email.onEvent });
async function issue(ctx: ActionCtx, token: string, requestId: string, channel: string) {
  const digest = tokenDigest(token);
  const info = await ctx.runQuery(internal.store.challengeContext, { tokenDigest: digest, requestId, channel });
  const code = String(randomInt(0, 100_000_000)).padStart(8, '0');
  const id = await ctx.runMutation(internal.store.issue, { tokenDigest: digest, requestId, channel, generation: info.generation, email: info.email, role: info.role, codeHmac: codeDigest(requestId, channel, info.generation, info.role, code) });
  try {
    const text = `${info.label}\nRequest: ${requestId}\nPurpose: ${info.request.purpose}\nGeneration: ${info.generation}\n\n${channel === 'authorize' ? 'Sharing this code authorizes only the complete changes below.' : 'This code verifies the proposed inbox. It does not authorize the changes.'}\n\n${info.request.diff.join('\n')}\n\nCode: ${code}\nExpires in 10 minutes. Only share it if you agree. A newer email replaces this generation.\n`;
    const emailJobId = await mail().sendEmail(ctx, { from: env('EMAIL_FROM'), to: info.email, subject: `Curated Phone: ${channel === 'authorize' ? 'approve exact changes' : 'verify guardian inbox'}`, text });
    await ctx.runMutation(internal.store.emailResult, { id, generation: info.generation, delivery: 'provider_queued', emailJobId });
  } catch {
    await ctx.runMutation(internal.store.emailResult, { id, generation: info.generation, delivery: 'failed' });
  }
}
async function signedApproval(ctx: ActionCtx, digest: string, requestId: string) {
  const info = await ctx.runQuery(internal.store.signingContext, { tokenDigest: digest, requestId });
  if (info.existing) return info.existing;
  const payload = canonical(info.payload);
  const signature = sign('sha256', Buffer.from(payload, 'utf8'), { key: env('APPROVAL_SIGNING_PRIVATE_KEY'), dsaEncoding: 'der' }).toString('base64');
  return await ctx.runMutation(internal.store.saveApproval, { tokenDigest: digest, requestId, payload, signature, keyId: env('APPROVAL_SIGNING_KEY_ID') });
}
export const createPairing = internalAction({ args: { label: v.string(), primaryEmail: v.string(), backupEmail: v.string() }, handler: async (ctx, a) => {
  const primaryEmail = email(a.primaryEmail), backupEmail = email(a.backupEmail);
  if (primaryEmail === backupEmail) throw new Error('Use two different inboxes');
  const code = randomBytes(16).toString('hex');
  await ctx.runMutation(internal.store.createPairing, { digest: tokenDigest(code), label: a.label.slice(0, 100), primaryEmail, backupEmail });
  return { pairingCode: code, expiresInSeconds: 600 };
} });
export const dispatch = internalAction({ args: { path: v.string(), token: v.string(), body: v.any() }, handler: async (ctx, a): Promise<any> => {
  const b = a.body ?? {};
  if (a.path === '/device/pair') {
    if (typeof b.code !== 'string' || !/^[a-f0-9]{32}$/.test(b.code)) throw new Error('Invalid pairing code');
    const token = randomBytes(32).toString('base64url');
    const result = await ctx.runMutation(internal.store.pair, { digest: tokenDigest(b.code), tokenDigest: tokenDigest(token), deviceId: randomUUID(), platform: String(b.platform ?? '') });
    return { ...result, token };
  }
  if (!/^[\w-]{43}$/.test(a.token)) throw new Error('Unauthorized');
  const digest = tokenDigest(a.token);
  if (a.path === '/device/state') {
    const state = await ctx.runQuery(internal.store.state, { tokenDigest: digest });
    if (state.pending && ['authorized', 'failed'].includes(state.pending.status) && !state.approval) state.approval = await signedApproval(ctx, digest, state.pending.requestId);
    return state;
  }
  if (a.path === '/requests') {
    const snapshot = normalizeSnapshot(b.snapshot);
    const requestId = randomUUID();
    const contentDigest = createHash('sha256').update(canonical({ snapshot, purpose: b.purpose, recovery: b.recovery === true, basePolicyVersion: b.basePolicyVersion, baseGuardianVersion: b.baseGuardianVersion })).digest('hex');
    const result = await ctx.runMutation(internal.store.submit, { tokenDigest: digest, requestId, purpose: b.purpose, recovery: b.recovery === true, snapshot, digest: contentDigest, basePolicyVersion: b.basePolicyVersion, baseGuardianVersion: b.baseGuardianVersion });
    for (const channel of result.channels) await issue(ctx, a.token, requestId, channel.channel);
    return { requestId };
  }
  if (a.path === '/requests/resend') { await issue(ctx, a.token, b.requestId, b.channel); return { ok: true }; }
  if (a.path === '/device/feed-status') return await ctx.runMutation(internal.store.feedStatus, { tokenDigest: digest, feedId: b.feedId, url: b.url, checksum: b.checksum, fetchedAt: b.fetchedAt });
  if (a.path === '/requests/cancel') return await ctx.runMutation(internal.store.cancel, { tokenDigest: digest, requestId: b.requestId });
  if (a.path === '/device/applied') return await ctx.runMutation(internal.store.applied, { tokenDigest: digest, requestId: b.requestId, approvalId: b.approvalId, version: b.version, success: b.success, failure: b.failure, ownershipReleased: b.ownershipReleased });
  if (a.path === '/requests/verify') {
    const state = await ctx.runQuery(internal.store.state, { tokenDigest: digest });
    const r = state.history.find(r => r.requestId === b.requestId);
    if (!r) throw new Error('Request not found');
    if (['authorized', 'failed', 'applied'].includes(r.status)) return { ok: true, authorized: true, approval: await signedApproval(ctx, digest, r.requestId) };
    const role = b.channel === 'authorize' ? (r.recovery ? 'recovery' : 'primary') : 'new_inbox';
    const result = await ctx.runMutation(internal.store.verify, { tokenDigest: digest, requestId: b.requestId, channel: b.channel, generation: b.generation, codeHmac: codeDigest(b.requestId, b.channel, b.generation, role, String(b.code)), approvalId: randomUUID() });
    return { ...result, ...(result.ok && result.authorized ? { approval: await signedApproval(ctx, digest, r.requestId) } : {}) };
  }
  throw new Error('Unknown endpoint');
} });
