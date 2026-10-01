/** Enforce body bounds while reading; absent Content-Length cannot bypass the cap. */
export async function readBoundedBody(request: Request | Response, limit: number): Promise<string> {
 const declared = request.headers.get('content-length');
 if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > limit)) throw new Error('REQUEST_TOO_LARGE');
 if (!request.body) return '';
 const reader = request.body.getReader(), decoder = new TextDecoder('utf-8', { fatal: true });
 let size = 0, text = '';
 try {
  for (;;) {
   const { value, done } = await reader.read(); if (done) break;
   size += value.byteLength;
   if (size > limit) { await reader.cancel(); throw new Error('REQUEST_TOO_LARGE'); }
   text += decoder.decode(value, { stream: true });
  }
  return text + decoder.decode();
 } finally { reader.releaseLock(); }
}
