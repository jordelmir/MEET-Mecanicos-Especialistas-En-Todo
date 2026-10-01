import { readFileSync } from 'node:fs';
import { canonicalCustodyV2, sha256Hex } from '../../packages/elysium-safety-core/src/custody';
const fixture=JSON.parse(readFileSync(new URL('./fixtures/safety-custody-v2.json',import.meta.url),'utf8'));
const actual=await sha256Hex(new TextEncoder().encode(canonicalCustodyV2(fixture.event)));
if(actual!==fixture.expectedHash) throw new Error('Safety custody TS parity failed');
console.log(actual);
