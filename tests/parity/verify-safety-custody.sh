#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
task_tmp="$(mktemp -d "${TMPDIR:-/tmp}/safety-custody-parity.XXXXXX")"
trap 'rm -rf -- "$task_tmp"' EXIT
cd "$task_root"
npx tsx tests/parity/safety-custody-parity.ts > "$task_tmp/ts.hash"
node --input-type=module - "$task_tmp/fixture.txt" <<'JS'
import {readFileSync,writeFileSync} from 'node:fs';
const f=JSON.parse(readFileSync('tests/parity/fixtures/safety-custody-v2.json','utf8'));
const e=f.event;
const fields=['MEET-SAFETY-CUSTODY-V2',e.eventId,e.evidenceId,e.previousHash??'',e.eventType,e.actor,e.reasonCode,e.serverSha256??'',e.occurredAtUtc];
writeFileSync(process.argv[2],[...fields.slice(1),f.expectedHash,fields.join('\x1f')].join('\n')+'\n');
JS
kotlinc packages/elysium-safety-core/kotlin/src/main/kotlin/io/elysium/safety/CustodyCanonicalV2.kt \
  tests/parity/SafetyCustodyParityMain.kt -include-runtime -d "$task_tmp/parity.jar"
java -jar "$task_tmp/parity.jar" "$task_tmp/fixture.txt" > "$task_tmp/kotlin.hash"
diff -u "$task_tmp/ts.hash" "$task_tmp/kotlin.hash"
echo 'Safety custody TS/Kotlin parity: PASS (PostgreSQL hash checked by closure integration)'
