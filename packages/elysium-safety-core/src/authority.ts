export type ClaimTruthState = 'ALLEGED'|'DOCUMENTED'|'CORROBORATED'|'STRONGLY_CORROBORATED'|'DISPUTED'|'RETRACTED';
export interface ProvenanceSource { sourceId:string; clusterId:string; kind:'JOURNALISTIC'|'PUBLIC_RECORD'|'INSTITUTIONAL'|'RESEARCH'|'ANONYMOUS'|'UNKNOWN'; withdrawn:boolean; }
/** Count independent source clusters, never raw repetitions or account count. */
export function independentSourceClusters(sources:readonly ProvenanceSource[]): number {
  return new Set(sources.filter(s=>!s.withdrawn && s.clusterId.trim() && !['ANONYMOUS','UNKNOWN'].includes(s.kind)).map(s=>s.clusterId)).size;
}
export interface PublicationAuthority {
  state:ClaimTruthState; reviewerId:string; publisherId:string;
  reviewerAal:number; publisherAal:number; reviewedVersion:number; currentVersion:number;
  serverVersion:number; sourceClusters:number; withdrawn:boolean; bytesVerified:boolean;
}
/** Deterministic eligibility predicate; server role checks are still mandatory. */
export function publicationEligible(authority:PublicationAuthority): boolean {
  return ['DOCUMENTED','CORROBORATED','STRONGLY_CORROBORATED'].includes(authority.state) &&
    !!authority.reviewerId && !!authority.publisherId && authority.reviewerId !== authority.publisherId &&
    authority.reviewerAal>=2 && authority.publisherAal>=2 &&
    Number.isSafeInteger(authority.currentVersion) && authority.currentVersion>0 &&
    authority.reviewedVersion===authority.currentVersion && authority.serverVersion>0 &&
    authority.sourceClusters>=1 && !authority.withdrawn && authority.bytesVerified;
}
