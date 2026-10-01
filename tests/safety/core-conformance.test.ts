import { describe,it,expect } from 'vitest';
import { independentSourceClusters,publicationEligible,publicArea,releasableAggregate,relayEligible,canonicalCustodyV2 } from '../../packages/elysium-safety-core/src/index';
import { correlateAggregates, RestrictedFinancialProvider, type FinancialAggregateSignal, type DocumentedPatternObservation } from '../../lib/safety/fi_analytics/FinancialCorrelationSandbox';

describe('reusable Safety core',()=>{
  it('counts sources by cluster and excludes withdrawn/anonymous records',()=>{
    expect(independentSourceClusters([
      {sourceId:'a',clusterId:'wire1',kind:'JOURNALISTIC',withdrawn:false},
      {sourceId:'b',clusterId:'wire1',kind:'PUBLIC_RECORD',withdrawn:false},
      {sourceId:'c',clusterId:'wire2',kind:'ANONYMOUS',withdrawn:false},
      {sourceId:'d',clusterId:'wire3',kind:'INSTITUTIONAL',withdrawn:true},
    ])).toBe(1);
  });
  it('requires different reviewers, live versions and verified bytes',()=>{
    const authority={state:'DOCUMENTED' as const,reviewerId:'r',publisherId:'p',reviewerAal:2,publisherAal:2,
      reviewedVersion:2,currentVersion:2,serverVersion:1,sourceClusters:1,withdrawn:false,bytesVerified:true};
    expect(publicationEligible(authority)).toBe(true);
    expect(publicationEligible({...authority,publisherId:'r'})).toBe(false);
    expect(publicationEligible({...authority,currentVersion:3})).toBe(false);
    expect(publicationEligible({...authority,bytesVerified:false})).toBe(false);
  });
  it('discloses areas and suppresses small or current cells',()=>{
    expect(publicArea(9.93333,-84.08333).disclosure).toBe('COARSE_GRID_25KM_PLUS');
    expect(publicArea(90,180).centerLatitude).toBeLessThan(90);
    expect(()=>publicArea(Number.NaN,0)).toThrow();
    expect(releasableAggregate(2,1,1000,3,100)).toBeNull();
    expect(releasableAggregate(9,950,1000,3,100)).toBeNull();
    expect(releasableAggregate(9,800,1000,3,100)).toBe(9);
  });
  it('rejects expired, replayed and hop-exhausted envelopes',()=>{
    const e={messageId:'m',senderKeyId:'s',recipientKeyId:'r',ciphertext:new Uint8Array([1]),hopCount:0,maxHops:4,createdAtMs:10,expiresAtMs:100};
    expect(relayEligible(e,20,false)).toBe(true);
    expect(relayEligible(e,20,true)).toBe(false);
    expect(relayEligible({...e,hopCount:4},20,false)).toBe(false);
    expect(relayEligible(e,101,false)).toBe(false);
  });
  it('rejects ambiguous custody delimiters',()=>{
    expect(()=>canonicalCustodyV2({eventId:'a',evidenceId:'b',previousHash:null,eventType:'SERVER_VERIFIED\u001fFORGED',actor:'SERVER',reasonCode:'BYTE_MATCH',serverSha256:null,occurredAtUtc:'2026-09-30T00:00:00.000000Z'})).toThrow();
  });
});
describe('financial correlation sandbox',()=>{
  const observations:DocumentedPatternObservation[]=Array.from({length:6},(_,i)=>({cellId:'grid025:400:400',periodStartMs:i*100,periodEndMs:i*100+99,documentedClaimCount:i+1,independentSourceClusters:3,serverVersion:1}));
  const signals:FinancialAggregateSignal[]=observations.map((o,i)=>({...o,typologyCode:'SYNTHETIC_ACTIVITY',normalizedValue:i/5,sourceAuthority:'SYNTHETIC_TEST_FIXTURE',datasetClass:'SYNTHETIC'}));
  it('returns hypotheses, preserves synthetic provenance and never guilt',async()=>{
    const result=await correlateAggregates({signals:async()=>signals},observations,0,600);
    expect(result).toHaveLength(1);expect(result[0].correlation).toBeCloseTo(1);
    expect(result[0].state).toBe('REVIEW_REQUIRED');expect(result[0].datasetClass).toBe('SYNTHETIC');
  });
  it('rejects duplicate windows and unauthorized financial signals',async()=>{
    await expect(correlateAggregates({signals:async()=>[...signals,signals[0]]},observations,0,600)).rejects.toThrow('DUPLICATE');
    await expect(correlateAggregates({signals:async()=>[{...signals[0],normalizedValue:Infinity}]},observations,0,600)).rejects.toThrow('UNAUTHORIZED');
  });
  it('blocks real protected financial data',async()=>{
    await expect(new RestrictedFinancialProvider().signals()).rejects.toThrow('FIU_AGREEMENT');
  });
});
