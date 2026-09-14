import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
const app = JSON.parse(read('package.json'));
const lock = JSON.parse(read('package-lock.json'));
const android = read('android/app/build.gradle.kts');
const androidVersion = android.match(/^\s*versionName = "([^"]+)"/m)?.[1];
const androidCode = Number(android.match(/^\s*versionCode = (\d+)\s*$/m)?.[1]);

assert.match(app.version, /^\d+\.\d+\.\d+$/, 'Use a numeric product release version');
assert.equal(lock.version, app.version, 'Lockfile product version must match package.json');
assert.equal(lock.packages[''].version, app.version, 'Lockfile root package version must match package.json');
assert.equal(androidVersion, app.version, 'Android and web product versions must match');
assert.ok(Number.isSafeInteger(androidCode) && androidCode > 0, 'Android versionCode must be a positive integer');
console.log(`Version contract: Android/web ${app.version}, Android versionCode ${androidCode}`);
