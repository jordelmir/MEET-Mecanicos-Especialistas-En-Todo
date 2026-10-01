/** Google's decoded token describes the app/device, never humanity or source independence. */
export interface IntegrityPolicy { packageName: string; certificateSha256: readonly string[]; minVersionCode: number; nowMillis: number }
function record(value: unknown): Record<string, unknown> { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
export function evaluateIntegrity(decoded: unknown, expectedHash: string, policy: IntegrityPolicy): { verified: boolean; reason: string } {
 const payload = record(record(decoded).tokenPayloadExternal);
 if (!Object.keys(payload).length || !expectedHash || policy.certificateSha256.length === 0) return { verified: false, reason: 'PROVIDER_VERDICT_UNAVAILABLE' };
 const request = record(payload.requestDetails), app = record(payload.appIntegrity), device = record(payload.deviceIntegrity), account = record(payload.accountDetails);
 const timestamp = Number(request.timestampMillis);
 if (request.requestHash !== expectedHash || request.requestPackageName !== policy.packageName || !Number.isFinite(timestamp)
 || timestamp > policy.nowMillis + 30_000 || timestamp < policy.nowMillis - 300_000) return { verified: false, reason: 'CHALLENGE_BINDING_OR_TIME_MISMATCH' };
 if (app.appRecognitionVerdict !== 'PLAY_RECOGNIZED' || app.packageName !== policy.packageName
 || !Array.isArray(app.certificateSha256Digest) || !app.certificateSha256Digest.some(v => typeof v === 'string' && policy.certificateSha256.includes(v))
 || !/^\d+$/.test(String(app.versionCode)) || Number(app.versionCode) < policy.minVersionCode) return { verified: false, reason: 'APP_INTEGRITY_REJECTED' };
 if (!Array.isArray(device.deviceRecognitionVerdict) || !device.deviceRecognitionVerdict.includes('MEETS_DEVICE_INTEGRITY') || account.appLicensingVerdict !== 'LICENSED') return { verified: false, reason: 'DEVICE_OR_LICENSE_REJECTED' };
 return { verified: true, reason: 'PLAY_INTEGRITY_VERIFIED' };
}
