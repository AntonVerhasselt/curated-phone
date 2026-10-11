import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
const plan = readFileSync('docs/implementation-plan.md', 'utf8');
const start = '<!-- ' + 'DEFAULT-BLOCKLIST-START -->', end = '<!-- ' + 'DEFAULT-BLOCKLIST-END -->';
if (plan.split(start).length !== 2 || plan.split(end).length !== 2) throw new Error('Expected one marked blocklist');
const ids = plan.split(start)[1].split(end)[0].split('\n').flatMap(line => {
  if (!line.startsWith('| ')) return [];
  const m = line.match(/\|\s*`([A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+)`\s*\|/);
  return m ? [m[1]] : [];
});
const blockedAppPackageIds = [...new Set(ids)].sort();
if (blockedAppPackageIds.length !== 200 || blockedAppPackageIds.includes('com.android.chrome')) throw new Error('Seed must contain 200 entries and keep Chrome allowed');
mkdirSync('android/app/src/main/assets', { recursive: true });
writeFileSync('android/app/src/main/assets/default-blocked-apps.json', JSON.stringify({ seedVersion: 1, appPolicyMode: 'blocklist', blockedAppPackageIds }, null, 2) + '\n');
console.log(`Exported ${blockedAppPackageIds.length} exact package IDs`);
