import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
const values = JSON.parse(readFileSync(process.argv[2] ?? '../.secrets/development/convex-secrets.json', 'utf8'));
for (const [name, value] of Object.entries(values)) {
  const result = spawnSync('npx', ['convex', 'env', 'set', name, ...(process.argv.includes('--prod') ? ['--prod'] : [])], { encoding: 'utf8', input: value, stdio: ['pipe', 'pipe', 'pipe'] });
  if (result.status !== 0) throw new Error(`Failed to configure ${name}; inspect CLI permissions.`);
  console.log(`Configured ${name}`);
}
