import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { spawnSync } from 'node:child_process';
const config = JSON.parse(readFileSync(process.argv[2] ?? `${homedir()}/.local/share/curated-phone/signing/signing-config.json`, 'utf8'));
for (const name of ['CURATED_BACKEND_URL', 'CURATED_APPROVAL_KEY_ID', 'CURATED_APPROVAL_PUBLIC_KEY_FILE']) if (!process.env[name]) throw new Error(`Export ${name} for the intended deployment before release signing`);
const result = spawnSync('./gradlew', [':app:assembleRelease', '--console=plain', '--no-configuration-cache'], { cwd: 'android', env: { ...process.env, ...config }, stdio: 'inherit' });
process.exitCode = result.status ?? 1;
