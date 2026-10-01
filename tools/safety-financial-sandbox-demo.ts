import {correlateAggregates,type DocumentedPatternObservation,type FinancialAggregateSignal} from '../lib/safety/fi_analytics/FinancialCorrelationSandbox';

// Explicit synthetic laboratory data, never customer or FIU records.
const observations:DocumentedPatternObservation[]=Array.from({length:8},(_,i)=>({
  cellId:'grid025:400:400',periodStartMs:i*604800000,periodEndMs:(i+1)*604800000,
  documentedClaimCount:[5,8,6,10,7,12,9,11][i],independentSourceClusters:3,serverVersion:1,
}));
const signals:FinancialAggregateSignal[]=observations.map((o,i)=>({...o,
  typologyCode:'SYNTHETIC_AGGREGATE_ACTIVITY',normalizedValue:[.1,.3,.2,.6,.25,.8,.5,.7][i],
  sourceAuthority:'LABORATORY_SYNTHETIC_V1',datasetClass:'SYNTHETIC',
}));
const hypotheses=await correlateAggregates({signals:async()=>signals},observations,0,8*604800000);
console.log(JSON.stringify({datasetClass:'SYNTHETIC',productionVerified:false,
  interpretation:'Correlation hypothesis requiring review; no crime or guilt determination',hypotheses},null,2));
