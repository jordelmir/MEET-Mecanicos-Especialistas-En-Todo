export interface PublicArea { centerLatitude:number; centerLongitude:number; uncertaintyMeters:number; disclosure:'COARSE_GRID_25KM_PLUS'; cellId:string; }
/** Coarse cells are areas; caller precision is never public precision. */
export function publicArea(latitude:number,longitude:number): PublicArea {
  if (!Number.isFinite(latitude)||!Number.isFinite(longitude)||latitude < -90||latitude > 90||longitude < -180||longitude > 180) throw new Error('INVALID_COORDINATES');
  const y=Math.min(719,Math.floor((latitude+90)/0.25));
  const x=Math.min(1439,Math.floor((longitude+180)/0.25));
  return {centerLatitude:y*0.25-90+0.125,centerLongitude:x*0.25-180+0.125,
    uncertaintyMeters:25000,disclosure:'COARSE_GRID_25KM_PLUS',cellId:`grid025:${y}:${x}`};
}
/** Public aggregation cannot expose a single victim or current illicit event. */
export function releasableAggregate(count:number,occurredAtMs:number,nowMs:number,minCellCount:number,delayMs:number): number|null {
  if (![count,minCellCount,delayMs,occurredAtMs,nowMs].every(Number.isFinite) ||
    !Number.isSafeInteger(count)||!Number.isSafeInteger(minCellCount)||count<0||minCellCount<3||delayMs<0) throw new Error('INVALID_PUBLIC_POLICY');
  return count>=minCellCount && occurredAtMs<=nowMs-delayMs ? count : null;
}
