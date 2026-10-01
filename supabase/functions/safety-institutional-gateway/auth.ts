export interface JwtPolicy { issuer: string; audience: string; nowSeconds: number; maxLifetimeSeconds: number }
export type MachineClaims = Record<string, unknown> & { iss: string; sub: string; aud: string | string[]; exp: number; iat: number; scope: string; cnf: { 'x5t#S256': string } };
function bytes(value: string): Uint8Array { if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('INVALID_BASE64URL'); return Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - value.length % 4) % 4)), c => c.charCodeAt(0)); }
/** Only pinned issuer/JWKS, RS256 signatures and bounded machine JWTs are accepted. */
export async function verifyMachineJwt(token: string, jwks: { keys: JsonWebKey[] }, policy: JwtPolicy): Promise<MachineClaims> {
 if (token.length > 16384) throw new Error('TOKEN_TOO_LARGE');
 const parts = token.split('.'); if (parts.length !== 3) throw new Error('INVALID_JWT');
 const header = JSON.parse(new TextDecoder().decode(bytes(parts[0]))), claims = JSON.parse(new TextDecoder().decode(bytes(parts[1])));
 if (header.alg !== 'RS256' || typeof header.kid !== 'string' || header.crit || header.jku || header.jwk || header.x5u) throw new Error('UNSUPPORTED_JWT');
 const matches = jwks.keys.filter((key: JsonWebKey & { kid?: string }) => key.kid === header.kid && key.kty === 'RSA' && (!key.alg || key.alg === 'RS256') && (!key.use || key.use === 'sig'));
 if (matches.length !== 1) throw new Error('UNKNOWN_KEY');
 const key = await crypto.subtle.importKey('jwk', matches[0], { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
 if (!await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, bytes(parts[2]), new TextEncoder().encode(parts[0] + '.' + parts[1]))) throw new Error('INVALID_SIGNATURE');
 if (claims.iss !== policy.issuer || !(claims.aud === policy.audience || Array.isArray(claims.aud) && claims.aud.includes(policy.audience))
 || typeof claims.sub !== 'string' || claims.sub.length > 200 || !claims.sub
 || !Number.isFinite(claims.exp) || !Number.isFinite(claims.iat) || claims.exp <= policy.nowSeconds || claims.iat > policy.nowSeconds + 30
 || claims.exp - claims.iat > policy.maxLifetimeSeconds || claims.exp <= claims.iat
 || (claims.nbf !== undefined && (!Number.isFinite(claims.nbf) || claims.nbf > policy.nowSeconds + 30))
 || typeof claims.scope !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(claims.cnf?.['x5t#S256'] || '')) throw new Error('INVALID_MACHINE_CLAIMS');
 return claims;
}
export async function sha256Base64url(value: string): Promise<string> { return btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))))).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_'); }
/** A separately signed gateway assertion attests actual mTLS and binds its cert to this OAuth token. */
export function verifyGatewayBinding(machine: MachineClaims, assertion: MachineClaims, tokenDigest: string): void {
 if (assertion.mtls_verified !== true || assertion.token_sha256 !== tokenDigest
 || assertion.cnf['x5t#S256'] !== machine.cnf['x5t#S256']) throw new Error('MTLS_BINDING_REQUIRED');
}
