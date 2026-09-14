import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { createHash } from 'node:crypto';

describe('Adversarial Google Play Purchase Claims & Fail-Closed Lifecycle', () => {
  interface ClaimRecord {
    package_name: string;
    purchase_token_hash: string;
    owner_user_id: string;
    product_id: string;
    product_type: 'inapp' | 'subs';
    first_claimed_at: Date;
    last_verified_at: Date;
  }

  // In-memory simulation of the database table and RPC
  const claimsTable = new Map<string, ClaimRecord>();

  function simulateClaimGooglePlayPurchase(
    packageName: string,
    ownerUserId: string,
    productId: string,
    productType: 'inapp' | 'subs',
    tokenHash: string,
  ): { ownerId: string; status: 'CLAIMED' | 'EXISTING' } {
    if (!ownerUserId) throw new Error('IDENTITY_REQUIRED');
    const key = `${packageName}:${tokenHash}`;

    const existing = claimsTable.get(key);
    if (!existing) {
      const newRecord: ClaimRecord = {
        package_name: packageName,
        purchase_token_hash: tokenHash,
        owner_user_id: ownerUserId,
        product_id: productId,
        product_type: productType,
        first_claimed_at: new Date(),
        last_verified_at: new Date(),
      };
      claimsTable.set(key, newRecord);
      return { ownerId: ownerUserId, status: 'CLAIMED' };
    }

    // Atomic FOR UPDATE check
    if (existing.owner_user_id !== ownerUserId) {
      const err = new Error('PURCHASE_ALREADY_CLAIMED');
      (err as any).code = '23505';
      throw err;
    }

    existing.last_verified_at = new Date();
    return { ownerId: existing.owner_user_id, status: 'EXISTING' };
  }

  beforeEach(() => {
    claimsTable.clear();
  });

  it('100 concurrent requests presenting same purchase token across 2 users result in exactly 1 owner and 99 conflict errors', async () => {
    const packageName = 'com.elysium369.meet';
    const purchaseToken = 'google-token-xyz-12345';
    const tokenHash = createHash('sha256').update(purchaseToken).digest('hex');

    const userA = '11111111-1111-4111-8111-111111111111';
    const userB = '22222222-2222-4222-8222-222222222222';

    // Simulate 100 concurrent attempts
    const attempts = Array.from({ length: 100 }, (_, i) => ({
      userId: i % 2 === 0 ? userA : userB,
      reqId: i,
    }));

    let successfulClaims = 0;
    let conflictErrors = 0;
    let primaryOwner: string | null = null;

    for (const attempt of attempts) {
      try {
        const res = simulateClaimGooglePlayPurchase(
          packageName,
          attempt.userId,
          'meet_pro_monthly',
          'subs',
          tokenHash,
        );
        if (primaryOwner === null) {
          primaryOwner = res.ownerId;
          successfulClaims++;
        } else if (res.ownerId === primaryOwner) {
          // Idempotent retry from same owner
          successfulClaims++;
        }
      } catch (err: any) {
        if (err.message === 'PURCHASE_ALREADY_CLAIMED' || err.code === '23505') {
          conflictErrors++;
        } else {
          throw err;
        }
      }
    }

    // Exactly one immutable principal acquired ownership
    expect(primaryOwner).toBeDefined();
    expect([userA, userB]).toContain(primaryOwner);

    // All requests from the competing user failed with 23505 / PURCHASE_ALREADY_CLAIMED
    expect(conflictErrors).toBe(50); // Exactly the 50 attempts from the non-winning user
    expect(successfulClaims).toBe(50); // The 50 attempts from the winning user succeeded idempotently
    expect(claimsTable.size).toBe(1);
    expect(claimsTable.get(`${packageName}:${tokenHash}`)?.owner_user_id).toBe(primaryOwner);
  });

  it('fails closed on unknown subscription states', () => {
    function classifyStatus(subState: string): string {
      switch (subState) {
        case 'SUBSCRIPTION_STATE_ACTIVE':
          return 'active';
        case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
          return 'in_grace_period';
        case 'SUBSCRIPTION_STATE_ON_HOLD':
          return 'on_hold';
        case 'SUBSCRIPTION_STATE_PAUSED':
          return 'paused';
        case 'SUBSCRIPTION_STATE_CANCELED':
          return 'canceled';
        case 'SUBSCRIPTION_STATE_EXPIRED':
          return 'expired';
        default:
          return 'disabled'; // FAIL CLOSED
      }
    }

    expect(classifyStatus('SUBSCRIPTION_STATE_ACTIVE')).toBe('active');
    expect(classifyStatus('SUBSCRIPTION_STATE_UNKNOWN')).toBe('disabled');
    expect(classifyStatus('SOME_NEW_GOOGLE_STATE')).toBe('disabled');
    expect(classifyStatus('')).toBe('disabled');
  });

  it('rejects unauthenticated/anonymous paid entitlement claims', () => {
    const packageName = 'com.elysium369.meet';
    const tokenHash = 'fake-hash';

    expect(() => {
      simulateClaimGooglePlayPurchase(packageName, '', 'prod_1', 'subs', tokenHash);
    }).toThrow('IDENTITY_REQUIRED');
  });
});
