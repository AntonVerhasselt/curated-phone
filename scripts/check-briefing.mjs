import { readFileSync } from 'node:fs';
const seed = JSON.parse(readFileSync('android/app/src/main/assets/default-blocked-apps.json', 'utf8'));
if (seed.blockedAppPackageIds.length !== 200 || new Set(seed.blockedAppPackageIds).size !== 200 || seed.blockedAppPackageIds.includes('com.android.chrome')) throw new Error('Seed inventory is invalid');
for (const id of ['com.facebook.katana', 'com.instagram.android', 'com.zhiliaoapp.musically', 'com.google.android.youtube', 'com.linkedin.android']) if (!seed.blockedAppPackageIds.includes(id)) throw new Error('Initial social app missing');
console.log('Briefing seed integrity passed: exactly 200 packages, five social/video packages, stable Chrome allowed.');
