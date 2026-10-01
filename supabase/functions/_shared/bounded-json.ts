/** Read JSON with a byte limit before allocation, including chunked requests. */
export async function readBoundedJson(request: Request, maxBytes: number): Promise<unknown> {
  if (!request.body) throw new Error('INVALID_JSON');
  const declared=Number(request.headers.get('content-length'));
  if (Number.isFinite(declared)&&declared>maxBytes) throw new Error('REQUEST_TOO_LARGE');
  const reader=request.body.getReader();
  const chunks:Uint8Array[]=[];
  let total=0;
  try {
    for (;;) {
      const next=await reader.read();
      if(next.done) break;
      total+=next.value.byteLength;
      if(total>maxBytes) {await reader.cancel();throw new Error('REQUEST_TOO_LARGE');}
      chunks.push(next.value);
    }
    const data=new Uint8Array(total);let offset=0;
    for(const chunk of chunks) {data.set(chunk,offset);offset+=chunk.byteLength;}
    try{return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(data));}
    finally{data.fill(0);}
  } finally{for(const chunk of chunks) chunk.fill(0);reader.releaseLock();}
}
