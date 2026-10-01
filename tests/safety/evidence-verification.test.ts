import { describe, it, expect } from 'vitest';
import { createHash } from 'node:crypto';
import { MAX_EVIDENCE_BYTES, validEvidencePath, verifyEvidenceResponse, type EvidenceDescriptor } from '../../supabase/functions/safety-evidence-verify/handler';
import { readBoundedJson } from '../../supabase/functions/_shared/bounded-json';

const actor = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const report = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
const original = new TextEncoder().encode('immutable laboratory fixture');
const evidence: EvidenceDescriptor = { id, report_id: report, uploader_user_id: actor,
  storage_path: `${actor}/${report}/${id}.original`, byte_count: original.length,
  content_sha256: createHash('sha256').update(original).digest('hex') };

describe('server evidence byte verification', () => {
  it('bounds chunked JSON and multibyte UTF-8 before parsing',async()=>{
    await expect(readBoundedJson(new Request('https://local.test',{method:'POST',body:'"'+ 'é'.repeat(3000)+'"'}),4096)).rejects.toThrow('REQUEST_TOO_LARGE');
    expect(await readBoundedJson(new Request('https://local.test',{method:'POST',body:'{"id":1}'}),4096)).toEqual({id:1});
  });
  it('hashes the actual object bytes', async () => {
    const result = await verifyEvidenceResponse(evidence, new Response(original.slice()));
    expect(result).toEqual({state:'MATCH',sha256:evidence.content_sha256,byteCount:original.length});
  });
  it('rejects a digest lie despite equal lengths', async () => {
    const altered = original.slice(); altered[0] ^= 1;
    expect((await verifyEvidenceResponse(evidence,new Response(altered))).state).toBe('MISMATCH');
  });
  it('compares byte count as well as hash', async () => {
    expect((await verifyEvidenceResponse({...evidence,byte_count:original.length+1},new Response(original.slice()))).state).toBe('MISMATCH');
  });
  it('keeps storage failure separate from mismatch', async () => {
    expect((await verifyEvidenceResponse(evidence,new Response(null,{status:404}))).state).toBe('ERROR');
  });
  it('quarantines oversized declaration before reading', async () => {
    expect((await verifyEvidenceResponse({...evidence,byte_count:MAX_EVIDENCE_BYTES+1},new Response(original.slice()))).state).toBe('QUARANTINED');
  });
  it('bounds dishonest and chunked streams', async () => {
    const stream = new ReadableStream<Uint8Array>({start(controller){controller.enqueue(new Uint8Array(MAX_EVIDENCE_BYTES));controller.enqueue(new Uint8Array(1));controller.close();}});
    expect((await verifyEvidenceResponse(evidence,new Response(stream))).state).toBe('QUARANTINED');
  });
  it('rejects cross owner and path traversal before elevated access', () => {
    expect(validEvidencePath(evidence,actor)).toBe(true);
    expect(validEvidencePath({...evidence,storage_path:'../../private'},actor)).toBe(false);
    expect(validEvidencePath(evidence,report)).toBe(false);
  });
});
