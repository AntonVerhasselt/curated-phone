export const categories = ['social', 'streaming', 'gambling', 'adult'] as const;
export type Category = typeof categories[number];
export type Policy = { schemaVersion: 1; appPolicyMode: 'blocklist'; blockedAppPackageIds: string[]; categories: Category[]; feeds: FeedSource[]; customDomains: string[] };
export type FeedSource = { id: string; category: Category; url: string; licence: string };
export type Snapshot = { policy: Policy; primaryEmail: string; backupEmail: string };
export type Purpose = 'settings' | 'replace_primary' | 'replace_backup' | 'remove_management';
export const protectedPackages = new Set([
  'be.anton.curatedphone', 'com.android.chrome', 'com.google.android.gms', 'com.google.android.gsf',
  'com.android.vending', 'com.sec.android.app.launcher', 'com.android.systemui', 'android',
  'com.android.settings', 'com.android.permissioncontroller', 'com.google.android.permissioncontroller',
  'com.android.packageinstaller', 'com.google.android.packageinstaller', 'com.android.phone',
  'com.samsung.android.dialer', 'com.google.android.webview', 'com.android.webview',
  'be.belfius.directmobile.android', 'be.bmid.itsme', 'com.whatsapp', 'com.waze',
  'com.google.android.apps.maps', 'com.google.android.projection.gearhead',
]);
const unique = (values: string[]) => [...new Set(values)].sort();
export function email(value: string) {
  const result = value.trim().toLowerCase();
  if (result.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(result)) throw new Error('Invalid email address');
  return result;
}
export function domain(value: string) {
  const result = value.trim().toLowerCase().replace(/\.$/, '');
  if (result.length > 253 || !/^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/.test(result)) throw new Error('Enter a domain without a path, scheme, or wildcard');
  return result;
}
export function normalizePolicy(input: Policy): Policy {
  if (input.schemaVersion !== 1 || input.appPolicyMode !== 'blocklist') throw new Error('Unsupported policy schema');
  const apps = unique(input.blockedAppPackageIds);
  if (apps.length > 500 || apps.some(p => !/^[a-zA-Z][\w]*(?:\.[a-zA-Z][\w]*)+$/.test(p) || protectedPackages.has(p))) throw new Error('Invalid or protected package');
  const selected = unique(input.categories) as Category[];
  if (selected.some(c => !categories.includes(c))) throw new Error('Unknown website category');
  const domains = unique(input.customDomains.map(domain));
  if (domains.length > 1000 || input.feeds.length > 16) throw new Error('Policy is too large');
  const feeds = [...input.feeds].sort((a, b) => a.id.localeCompare(b.id));
  if (new Set(feeds.map(f => f.id)).size !== feeds.length) throw new Error('Duplicate feed');
  for (const feed of feeds) {
    if (!/^[\w-]{1,80}$/.test(feed.id) || !categories.includes(feed.category) || !feed.licence.trim() || feed.licence.length > 200) throw new Error('Invalid feed metadata');
    const url = new URL(feed.url);
    if (url.protocol !== 'https:' || url.username || url.password || feed.url.length > 1000) throw new Error('Feeds require HTTPS');
  }
  for (const c of selected) if (!feeds.some(f => f.category === c)) throw new Error(`Select an approved feed for ${c}`);
  return { schemaVersion: 1, appPolicyMode: 'blocklist', blockedAppPackageIds: apps, categories: selected, feeds, customDomains: domains };
}
export function normalizeSnapshot(value: Snapshot): Snapshot {
  const primaryEmail = email(value.primaryEmail), backupEmail = email(value.backupEmail);
  if (primaryEmail === backupEmail) throw new Error('Use two different guardian inboxes');
  return { policy: normalizePolicy(value.policy), primaryEmail, backupEmail };
}
export function canonical(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value !== null && typeof value === 'object') return '{' + Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => JSON.stringify(k) + ':' + canonical(v)).join(',') + '}';
  return JSON.stringify(value);
}
export function diff(before: Snapshot | null, after: Snapshot): string[] {
  const result: string[] = [];
  const old = before?.policy;
  const changes = (previous: string[], next: string[], add: string, remove: string) => {
    for (const value of next) if (!previous.includes(value)) result.push(`${add}: ${value}`);
    for (const value of previous) if (!next.includes(value)) result.push(`${remove}: ${value}`);
  };
  changes(old?.blockedAppPackageIds ?? [], after.policy.blockedAppPackageIds, 'Block app', 'Unblock app');
  changes(old?.categories ?? [], after.policy.categories, 'Block category', 'Stop blocking category');
  changes(old?.customDomains ?? [], after.policy.customDomains, 'Block domain', 'Unblock domain');
  changes((old?.feeds ?? []).map(canonical), after.policy.feeds.map(canonical), 'Use feed', 'Remove feed');
  if (before?.primaryEmail !== after.primaryEmail) result.push(`Primary guardian: ${before?.primaryEmail ?? '(initial setup)'} → ${after.primaryEmail}`);
  if (before?.backupEmail !== after.backupEmail) result.push(`Recovery guardian: ${before?.backupEmail ?? '(initial setup)'} → ${after.backupEmail}`);
  return result;
}
export function validatePurpose(purpose: Purpose, recovery: boolean, before: Snapshot | null, after: Snapshot) {
  if (recovery && purpose !== 'replace_primary') throw new Error('Recovery may only replace the primary guardian');
  if (!before) {
    if (purpose !== 'settings' || recovery) throw new Error('Initial setup requires primary approval');
    return;
  }
  const samePolicy = canonical(before.policy) === canonical(after.policy);
  if (purpose === 'settings' && (before.primaryEmail !== after.primaryEmail || before.backupEmail !== after.backupEmail)) throw new Error('Guardian replacement must be standalone');
  if (purpose === 'replace_primary' && (!samePolicy || before.backupEmail !== after.backupEmail || before.primaryEmail === after.primaryEmail)) throw new Error('Primary replacement must only change its address');
  if (purpose === 'replace_backup' && (!samePolicy || before.primaryEmail !== after.primaryEmail || before.backupEmail === after.backupEmail)) throw new Error('Recovery replacement must only change its address');
  if (purpose === 'remove_management' && canonical(before) !== canonical(after)) throw new Error('Removal must be standalone');
}
