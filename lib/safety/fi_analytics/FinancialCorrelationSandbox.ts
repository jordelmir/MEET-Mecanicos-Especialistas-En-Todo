/** Aggregate-only research domain; it cannot query accounts or promote guilt. */
export interface FinancialAggregateSignal {
  cellId:string; periodStartMs:number; periodEndMs:number; typologyCode:string;
  normalizedValue:number; sourceAuthority:string; datasetClass:'SYNTHETIC'|'OPEN_AGGREGATE';
}
export interface DocumentedPatternObservation {
  cellId:string; periodStartMs:number; periodEndMs:number; documentedClaimCount:number;
  independentSourceClusters:number; serverVersion:number;
}
export interface FinancialAggregateSignalProvider {
  signals(fromMs:number,toMs:number,cells:ReadonlySet<string>):Promise<readonly FinancialAggregateSignal[]>;
}
export interface CorrelationHypothesis {
  cellId:string; typologyCode:string; correlation:number; sampleCount:number;
  state:'REVIEW_REQUIRED'; datasetClass:'SYNTHETIC'|'OPEN_AGGREGATE';
}

/** Pearson correlation across exact aligned aggregate windows; no imputation. */
export async function correlateAggregates(provider:FinancialAggregateSignalProvider,
  observations:readonly DocumentedPatternObservation[],fromMs:number,toMs:number):Promise<CorrelationHypothesis[]> {
  if (!Number.isSafeInteger(fromMs)||!Number.isSafeInteger(toMs)||fromMs>=toMs) throw new Error('INVALID_RESEARCH_WINDOW');
  const authorized=observations.filter(o=>o.serverVersion>0 && o.independentSourceClusters>=3 &&
    o.periodStartMs>=fromMs && o.periodEndMs<=toMs && o.periodStartMs<o.periodEndMs &&
    Number.isSafeInteger(o.documentedClaimCount)&&o.documentedClaimCount>=0 && /^grid025:\d+:\d+$/.test(o.cellId));
  const cells=new Set(authorized.map(o=>o.cellId));
  const signals=await provider.signals(fromMs,toMs,cells);
  const grouped=new Map<string,Array<{signal:FinancialAggregateSignal;observed:number}>>();
  const seen=new Set<string>();
  for (const signal of signals) {
    if (!cells.has(signal.cellId)||!Number.isFinite(signal.normalizedValue)||signal.normalizedValue<0||signal.normalizedValue>1||
      !signal.sourceAuthority.trim()||!['SYNTHETIC','OPEN_AGGREGATE'].includes(signal.datasetClass)||
      !/^[A-Z0-9_]{3,64}$/.test(signal.typologyCode)) throw new Error('UNAUTHORIZED_FINANCIAL_SIGNAL');
    const observation=authorized.find(o=>o.cellId===signal.cellId&&o.periodStartMs===signal.periodStartMs&&o.periodEndMs===signal.periodEndMs);
    if (!observation) continue;
    const groupKey=`${signal.cellId}|${signal.typologyCode}|${signal.datasetClass}`;
    const windowKey=`${groupKey}|${signal.periodStartMs}|${signal.periodEndMs}`;
    if (seen.has(windowKey)) throw new Error('DUPLICATE_RESEARCH_WINDOW');
    seen.add(windowKey);
    const group=grouped.get(groupKey) ?? [];
    group.push({signal,observed:observation.documentedClaimCount});grouped.set(groupKey,group);
  }
  const hypotheses:CorrelationHypothesis[]=[];
  for (const values of grouped.values()) {
    if (values.length<6) continue;
    const meanX=values.reduce((sum,v)=>sum+v.observed,0)/values.length;
    const meanY=values.reduce((sum,v)=>sum+v.signal.normalizedValue,0)/values.length;
    let covariance=0,varianceX=0,varianceY=0;
    for (const value of values) {const x=value.observed-meanX,y=value.signal.normalizedValue-meanY;covariance+=x*y;varianceX+=x*x;varianceY+=y*y;}
    if (varianceX<=0||varianceY<=0) continue;
    const correlation=Math.max(-1,Math.min(1,covariance/Math.sqrt(varianceX*varianceY)));
    hypotheses.push({cellId:values[0].signal.cellId,typologyCode:values[0].signal.typologyCode,
      correlation,sampleCount:values.length,state:'REVIEW_REQUIRED',datasetClass:values[0].signal.datasetClass});
  }
  return hypotheses;
}

/** Real FIU/banking data has no adapter in the public application. */
export class RestrictedFinancialProvider implements FinancialAggregateSignalProvider {
  async signals():Promise<never> {throw new Error('FIU_AGREEMENT_TENANT_AUTHORITY_REVIEW_REQUIRED');}
}
