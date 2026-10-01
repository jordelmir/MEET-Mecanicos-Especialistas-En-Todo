export interface OfflineEnvelope {
  messageId:string; senderKeyId:string; recipientKeyId:string;
  ciphertext:Uint8Array; hopCount:number; maxHops:number; createdAtMs:number; expiresAtMs:number;
}
/** Pure routing eligibility. It performs no radio I/O and makes no crypto claim. */
export function relayEligible(envelope:OfflineEnvelope,nowMs:number,seen:boolean,maxBytes=1024*1024):boolean {
  return !seen && !!envelope.messageId && !!envelope.senderKeyId && !!envelope.recipientKeyId &&
    envelope.ciphertext.byteLength>0 && envelope.ciphertext.byteLength<=maxBytes &&
    Number.isSafeInteger(envelope.hopCount)&&Number.isSafeInteger(envelope.maxHops)&&
    envelope.hopCount>=0 && envelope.maxHops<=16 && envelope.hopCount<envelope.maxHops &&
    Number.isSafeInteger(envelope.createdAtMs)&&Number.isSafeInteger(envelope.expiresAtMs)&&
    envelope.createdAtMs<=nowMs+300000 && envelope.expiresAtMs>nowMs && envelope.expiresAtMs>envelope.createdAtMs &&
    envelope.expiresAtMs-envelope.createdAtMs<=7*86400000;
}
