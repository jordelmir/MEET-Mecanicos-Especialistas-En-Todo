import assert from 'node:assert/strict';
import { evaluateIntegrity } from '../../supabase/functions/safety-device-trust/policy.ts';
import { verifyMachineJwt, verifyGatewayBinding, sha256Base64url } from '../../supabase/functions/safety-institutional-gateway/auth.ts';
const now = 1800000000000, packageName = 'com.elysium369.meet';
const policy = { packageName, certificateSha256: ['pinned-cert'], minVersionCode: 70, nowMillis: now };
const payload = { tokenPayloadExternal: { requestDetails: { requestHash: 'bound-hash', requestPackageName: packageName, timestampMillis: now },
 appIntegrity: { appRecognitionVerdict: 'PLAY_RECOGNIZED', packageName, certificateSha256Digest: ['pinned-cert'], versionCode: '70' },
 deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY'] }, accountDetails: { appLicensingVerdict: 'LICENSED' } } };
assert.equal(evaluateIntegrity(payload, 'bound-hash', policy).verified, true);
for (const altered of [null, {}, { tokenPayloadExternal: {} }, { tokenPayloadExternal: { ...payload.tokenPayloadExternal, requestDetails: { ...payload.tokenPayloadExternal.requestDetails, requestHash: 'replay' } } },
 { tokenPayloadExternal: { ...payload.tokenPayloadExternal, appIntegrity: { ...payload.tokenPayloadExternal.appIntegrity, certificateSha256Digest: ['untrusted-cert'] } } },
 { tokenPayloadExternal: { ...payload.tokenPayloadExternal, requestDetails: { ...payload.tokenPayloadExternal.requestDetails, timestampMillis: now - 600000 } } },
 { tokenPayloadExternal: { ...payload.tokenPayloadExternal, deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_BASIC_INTEGRITY'] } } }]) {
 assert.equal(evaluateIntegrity(altered, 'bound-hash', policy).verified, false);
}
const pair = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']);
const publicKey = { ...await crypto.subtle.exportKey('jwk', pair.publicKey), kid: 'pinned' };
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
async function signed(claims: unknown, header: unknown = { alg: 'RS256', kid: 'pinned' }): Promise<string> {
 const unsigned = encode(header) + '.' + encode(claims);
 const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey, new TextEncoder().encode(unsigned));
 return unsigned + '.' + Buffer.from(signature).toString('base64url');
}
const claims = { iss: 'https://issuer.invalid', sub: 'institution-machine', aud: 'safety-gateway', iat: now / 1000, exp: now / 1000 + 240, scope: 'safety:read_public', cnf: { 'x5t#S256': 'a'.repeat(43) } };
const jwtPolicy = { issuer: claims.iss, audience: claims.aud, nowSeconds: now / 1000, maxLifetimeSeconds: 300 };
const token = await signed(claims), machine = await verifyMachineJwt(token, { keys: [publicKey] }, jwtPolicy);
assert.equal(machine.sub, claims.sub);
for (const rejected of [await signed({ ...claims, exp: now / 1000 - 1 }), await signed({ ...claims, aud: 'other-gateway' }), await signed({ ...claims, exp: now / 1000 + 86400 }), await signed(claims, { alg: 'none', kid: 'pinned' }), await signed(claims, { alg: 'RS256', kid: 'pinned', jku: 'https://attacker.invalid' })]) {
 await assert.rejects(verifyMachineJwt(rejected, { keys: [publicKey] }, jwtPolicy));
}
const gateway = { ...machine, mtls_verified: true, token_sha256: await sha256Base64url(token) };
verifyGatewayBinding(machine, gateway, await sha256Base64url(token));
assert.throws(() => verifyGatewayBinding(machine, { ...gateway, mtls_verified: false }, gateway.token_sha256));
assert.throws(() => verifyGatewayBinding(machine, gateway, 'wrong-token'));
assert.throws(() => verifyGatewayBinding(machine, { ...gateway, cnf: { 'x5t#S256': 'b'.repeat(43) } }, gateway.token_sha256));
console.log('Safety device trust + institutional gateway contracts: PASS');
