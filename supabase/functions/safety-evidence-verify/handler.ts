/** Byte verifier shared by the Edge entry point and adversarial tests. */
import { sha256Hex } from '../../../packages/elysium-safety-core/src/custody.ts';
export const MAX_EVIDENCE_BYTES = 20 * 1024 * 1024;
export interface EvidenceDescriptor {
  id: string;
  storage_path: string;
  content_sha256: string;
  byte_count: number;
  uploader_user_id: string;
  report_id: string;
}
export interface VerificationResult {
  state: 'MATCH' | 'MISMATCH' | 'QUARANTINED' | 'ERROR';
  sha256: string | null;
  byteCount: number | null;
}

/** Reject oversized streams before allocating their full contents. */
export async function verifyEvidenceResponse(evidence: EvidenceDescriptor, response: Response): Promise<VerificationResult> {
  if (!Number.isSafeInteger(evidence.byte_count) || evidence.byte_count <= 0 || evidence.byte_count > MAX_EVIDENCE_BYTES) {
    await response.body?.cancel();
    return { state: 'QUARANTINED', sha256: null, byteCount: null };
  }
  if (!response.ok || !response.body) {
    await response.body?.cancel();
    return { state: 'ERROR', sha256: null, byteCount: null };
  }
  const length = response.headers.get('content-length');
  if (length && Number(length) > MAX_EVIDENCE_BYTES) {
    await response.body.cancel();
    return { state: 'QUARANTINED', sha256: null, byteCount: null };
  }
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  let bytes: Uint8Array | undefined;
  try {
    for (;;) {
      const next = await reader.read();
      if (next.done) break;
      total += next.value.byteLength;
      if (total > MAX_EVIDENCE_BYTES) {
        next.value.fill(0);
        await reader.cancel();
        return { state: 'QUARANTINED', sha256: null, byteCount: total };
      }
      chunks.push(next.value);
    }
    bytes = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    const sha256 = await sha256Hex(bytes);
    return { state: sha256 === evidence.content_sha256 && total === evidence.byte_count ? 'MATCH' : 'MISMATCH', sha256, byteCount: total };
  } catch {
    return { state: 'ERROR', sha256: null, byteCount: null };
  } finally {
    bytes?.fill(0);
    for (const chunk of chunks) chunk.fill(0);
    reader.releaseLock();
  }
}

/** Bind private object paths to the authenticated owner and immutable IDs. */
export function validEvidencePath(evidence: EvidenceDescriptor, actor: string): boolean {
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  return [actor,evidence.id,evidence.report_id].every(id => uuid.test(id)) &&
    evidence.uploader_user_id === actor &&
    evidence.storage_path === `${actor}/${evidence.report_id}/${evidence.id}.original`;
}
