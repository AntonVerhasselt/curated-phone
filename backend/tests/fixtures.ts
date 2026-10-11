import type { Snapshot } from '../convex/lib/policy';
export const base: Snapshot = { policy: { schemaVersion: 1, appPolicyMode: 'blocklist', blockedAppPackageIds: ['com.instagram.android'], categories: [], feeds: [], customDomains: ['example.org'] }, primaryEmail: 'primary@example.org', backupEmail: 'backup@example.org' };
