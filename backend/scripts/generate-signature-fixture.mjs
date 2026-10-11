import { generateKeyPairSync, sign } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1', publicKeyEncoding: { type: 'spki', format: 'pem' } });
const snapshot = { policy: { schemaVersion: 1, appPolicyMode: 'blocklist', blockedAppPackageIds: ['com.instagram.android'], categories: [], feeds: [], customDomains: ['example.org'] }, primaryEmail: 'primary@example.org', backupEmail: 'backup@example.org' };
const payload = JSON.stringify({ schemaVersion: 1, deviceId: 'fixture-device', requestId: 'fixture-request', approvalId: 'fixture-approval', purpose: 'settings', requestDigest: 'fixture-digest', basePolicyVersion: 0, baseGuardianVersion: 0, targetPolicyVersion: 1, targetGuardianVersion: 1, snapshot, issuedAt: 1700000000000, expiresAt: 1700604800000 });
const fixture = { publicKey, envelope: { payload, signature: sign('sha256', Buffer.from(payload), { key: privateKey, dsaEncoding: 'der' }).toString('base64'), keyId: 'fixture-p256-1' } };
const directory = '../android/app/src/test/resources';
mkdirSync(directory, { recursive: true }); writeFileSync(`${directory}/node-signature.json`, JSON.stringify(fixture, null, 2));
console.log('Generated a Node P-256 DER fixture; no private key was saved.');
