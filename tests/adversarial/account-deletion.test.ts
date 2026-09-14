import { describe, it, expect } from 'vitest';
import { createHmac } from 'node:crypto';

describe('Account Deletion & Non-Reversible Tombstone Privacy Verification', () => {
  interface DeletionRequest {
    user_id: string;
    status: 'REQUESTED' | 'BLOCKED' | 'EXECUTING' | 'COMPLETED' | 'FAILED';
    failure_code?: string;
  }

  interface Tombstone {
    request_id: string;
    subject_digest: string;
    policy_version: string;
    completed_at: Date;
  }

  function simulateAccountDeletionRequest(
    userId: string,
    hasActiveRides: boolean,
  ): DeletionRequest {
    if (hasActiveRides) {
      return {
        user_id: userId,
        status: 'BLOCKED',
        failure_code: 'ACTIVE_RIDES_PENDING',
      };
    }
    return {
      user_id: userId,
      status: 'REQUESTED',
    };
  }

  function createTombstone(
    requestId: string,
    userId: string,
    pepper: string,
    policyVersion: string,
  ): Tombstone {
    // Subject digest is created with server-secret pepper and never contains PII
    const subjectDigest = createHmac('sha256', pepper).update(userId).digest('hex');
    return {
      request_id: requestId,
      subject_digest: subjectDigest,
      policy_version: policyVersion,
      completed_at: new Date(),
    };
  }

  it('blocks deletion when user has active or pending rides', () => {
    const res = simulateAccountDeletionRequest('user-1', true);
    expect(res.status).toBe('BLOCKED');
    expect(res.failure_code).toBe('ACTIVE_RIDES_PENDING');
  });

  it('allows deletion request when user has zero pending rides', () => {
    const res = simulateAccountDeletionRequest('user-2', false);
    expect(res.status).toBe('REQUESTED');
    expect(res.failure_code).toBeUndefined();
  });

  it('generates non-reversible, non-PII tombstone digest', () => {
    const pepper = 'server-secret-pepper-999';
    const tombstone = createTombstone('req-1', 'user-12345', pepper, 'privacy-v1');

    expect(tombstone.subject_digest).toBeDefined();
    expect(tombstone.subject_digest.length).toBe(64); // SHA-256 hex string

    // Tombstone must NOT contain raw user identifiers or PII
    expect(tombstone.subject_digest).not.toContain('user-12345');
    expect((tombstone as any).email).toBeUndefined();
    expect((tombstone as any).phone).toBeUndefined();
    expect((tombstone as any).gps).toBeUndefined();
  });
});
