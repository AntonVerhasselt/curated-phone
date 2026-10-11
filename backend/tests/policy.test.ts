import { describe, it, expect } from 'vitest';
import { normalizeSnapshot, validatePurpose, diff, canonical, type Snapshot } from '../convex/lib/policy';
import { base } from './fixtures';
describe('Exact policy and guardian roles', () => {
  it('sorts and deduplicates immutable policy contents', () => { expect(normalizeSnapshot({ ...base, policy: { ...base.policy, blockedAppPackageIds: ['com.z.app', 'com.a.app', 'com.z.app'] } }).policy.blockedAppPackageIds).toEqual(['com.a.app', 'com.z.app']); });
  it.each(['be.anton.curatedphone', 'com.android.chrome', 'com.google.android.gms', 'com.android.systemui', 'be.bmid.itsme'])('rejects protected package %s', packageId => { expect(() => normalizeSnapshot({ ...base, policy: { ...base.policy, blockedAppPackageIds: [packageId] } })).toThrow(); });
  it('rejects old allowlist schemas', () => { expect(() => normalizeSnapshot({ ...base, policy: { ...base.policy, appPolicyMode: 'allowlist' } as any })).toThrow(); });
  it('rejects malformed host filters and missing category feeds', () => { expect(() => normalizeSnapshot({ ...base, policy: { ...base.policy, customDomains: ['https://example.org/path'] } })).toThrow(); expect(() => normalizeSnapshot({ ...base, policy: { ...base.policy, categories: ['adult'] } })).toThrow(); });
  it('computes a complete diff from the actual snapshot', () => { expect(diff(base, { ...base, policy: { ...base.policy, blockedAppPackageIds: ['com.linkedin.android'] } })).toEqual(['Block app: com.linkedin.android', 'Unblock app: com.instagram.android']); });
  it.each(['settings', 'replace_backup', 'remove_management'] as const)('recovery cannot authorize %s', purpose => { expect(() => validatePurpose(purpose, true, base, base)).toThrow(); });
  it('recovery only replaces primary, without mixed settings or backup edits', () => {
    const after = { ...base, primaryEmail: 'new@example.org' };
    expect(() => validatePurpose('replace_primary', true, base, after)).not.toThrow();
    expect(() => validatePurpose('replace_primary', true, base, { ...after, policy: { ...base.policy, blockedAppPackageIds: [] } })).toThrow();
    expect(() => validatePurpose('replace_primary', true, base, { ...after, backupEmail: 'another@example.org' })).toThrow();
  });
  it('rejects removal mixed with edits and inline guardian changes', () => { expect(() => validatePurpose('remove_management', false, base, { ...base, primaryEmail: 'new@example.org' })).toThrow(); expect(() => validatePurpose('settings', false, base, { ...base, primaryEmail: 'new@example.org' })).toThrow(); });
  it('canonical bytes are independent of object property order', () => { expect(canonical({ b: 1, a: 2 })).toBe(canonical({ a: 2, b: 1 })); });
});
