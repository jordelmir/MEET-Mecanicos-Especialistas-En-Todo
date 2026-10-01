import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

// Independent implementation of the Mesh-only v1 ASCII/big-endian format.
// This fixture is resource/parity evidence, never crypto or physical-delivery proof.
const fixture = JSON.parse(readFileSync(process.argv[2] ?? 'tests/parity/fixtures/mesh-wire-v1.json', 'utf8'));
const utf = (value: string) => {
  assert.match(value, /^[\x20-\x7e]*$/);
  const body = Buffer.from(value, 'ascii'); assert.ok(body.length <= 128);
  const length = Buffer.alloc(2); length.writeUInt16BE(body.length);
  return Buffer.concat([length, body]);
};
const int = (value: number) => { const b = Buffer.alloc(4); b.writeInt32BE(value); return b; };
const long = (value: number) => { assert.ok(Number.isSafeInteger(value)); const b = Buffer.alloc(8); b.writeBigInt64BE(BigInt(value)); return b; };
const aad = Buffer.concat([
  int(0x45564d31), utf(fixture.messageId), utf(fixture.originKeyId), utf(fixture.recipientKeyId),
  long(fixture.createdAt), long(fixture.expiresAt), int(fixture.maxHops), int(fixture.priority),
  int(fixture.attachmentBytes), utf(fixture.attachmentDigest),
]);
const ciphertext = Buffer.from(fixture.ciphertextHex, 'hex');
const authentication = Buffer.from(fixture.authenticationHex, 'hex');
const wire = Buffer.concat([aad, int(fixture.hops), int(ciphertext.length), ciphertext, int(authentication.length), authentication]);
const packet = Buffer.concat([int(0x45565031), Buffer.from([1]), int(wire.length), wire]);
const computed = { aadHex: aad.toString('hex'), wireHex: wire.toString('hex'), packetHex: packet.toString('hex'), sha256: createHash('sha256').update(wire).digest('hex') };
assert.equal(computed.aadHex, fixture.expectedAadHex);
assert.equal(computed.wireHex, fixture.expectedWireHex);
assert.equal(computed.packetHex, fixture.expectedPacketHex);
assert.equal(computed.sha256, fixture.expectedSha256);
if (process.argv[3]) assert.deepEqual(JSON.parse(readFileSync(process.argv[3], 'utf8')), computed, 'Kotlin/TypeScript Mesh wire disagreement');
console.log(process.argv[3] ? '[OK] Mesh wire v1 TS/Kotlin byte parity' : '[OK] Mesh wire v1 TypeScript fixture; Kotlin output not supplied');
