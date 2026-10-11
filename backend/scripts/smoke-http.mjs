import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import assert from 'node:assert/strict';
const settings = Object.fromEntries(readFileSync('.env.local', 'utf8').split('\n').filter(l => /^[A-Z_]+=/.test(l)).map(l => { const at = l.indexOf('='); return [l.slice(0, at), l.slice(at + 1).split(' #')[0].trim()]; }));
const base = settings.CONVEX_SITE_URL;
function run(name, args) { const result = spawnSync('npx', ['convex', 'run', name, JSON.stringify(args)], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }); assert.equal(result.status, 0, `Internal setup failed: ${name}`); return result.stdout.trim() ? JSON.parse(result.stdout) : null; }
async function request(path, token, body) { const response = await fetch(base + path, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) }); return { status: response.status, body: await response.json() }; }
assert.equal((await request('/health')).body.status, 'ok');
assert.equal((await request('/device/state')).status, 401);
const pairing = run('cryptoActions:createPairing', { label: 'AUTOMATED TRANSPORT FIXTURE', primaryEmail: 'delivered@resend.dev', backupEmail: 'bounced@resend.dev' });
const paired = await request('/device/pair', null, { code: pairing.pairingCode, platform: 'Node HTTPS integration test' });
assert.equal(paired.status, 200); const { token, deviceId } = paired.body;
try {
  assert.equal((await request('/device/pair', null, { code: pairing.pairingCode })).status, 400);
  assert.equal((await request('/device/state', 'x'.repeat(43))).status, 401);
  const state = await request('/device/state', token); assert.equal(state.body.deviceId, deviceId);
  const snapshot = { policy: { schemaVersion: 1, appPolicyMode: 'blocklist', blockedAppPackageIds: ['com.instagram.android'], categories: [], feeds: [], customDomains: ['example.org'] }, primaryEmail: 'delivered@resend.dev', backupEmail: 'bounced@resend.dev' };
  const submitted = await request('/requests', token, { snapshot, purpose: 'settings', recovery: false, basePolicyVersion: 0, baseGuardianVersion: 0 }); assert.equal(submitted.status, 200);
  const pending = (await request('/device/state', token)).body;
  assert.equal(pending.pending.requestId, submitted.body.requestId); assert.equal(pending.challenges.length, 2);
  assert.equal(pending.approvedPolicyVersion, 0); assert.equal(pending.approval, null);
  const rejected = await request('/requests/verify', token, { requestId: submitted.body.requestId, channel: 'authorize', generation: 1, code: 'not-a-valid-otp' }); assert.equal(rejected.body.ok, false);
  assert.equal((await request('/device/state', token)).body.challenges.find(c => c.channel === 'authorize').attempts, 1);
  assert.equal((await request('/requests/cancel', token, { requestId: submitted.body.requestId })).status, 200);
  console.log('Hosted HTTPS integration passed: health, pairing, one-time consumption, authentication, immutable submission, OTP failure persistence, cancellation. No OTP or credential was printed.');
} finally { run('admin:discardTransportFixture', { deviceId }); }
