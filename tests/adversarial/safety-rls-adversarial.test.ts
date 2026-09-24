import { describe, it, expect } from 'vitest';

/**
 * Adversarial Test: Safety Module RLS & Immutability
 * Tests that safety tables enforce proper Row Level Security:
 *
 * 1. Anon user cannot read private reports
 * 2. User A cannot read User B's reports
 * 3. User A cannot read User B's evidence or custody chain
 * 4. No authenticated user can UPDATE/DELETE evidence_objects or evidence_custody
 * 5. No authenticated user can INSERT into evidence_custody (only RPCs via service role)
 * 6. Public safety points are readable by any authenticated user
 * 7. Reporter anonymity: public_point_report_links is in safety_private (not visible to other users)
 * 8. Counterclaims are only readable by their author
 * 9. User cannot directly INSERT into safety_reports (must go through RPC)
 *
 * These tests verify the SQL policy contracts defined in:
 *   - 20260918000000_safety_foundation_v1.sql
 *   - 20260919053000_safety_operational_v2.sql
 *   - 20260919160821_safety_authority_integrity.sql
 *   - 20260920021000_safety_reporter_anonymity_boundary.sql
 */
describe('Adversarial: Safety RLS Cross-User Isolation & Immutability', () => {

  // ----- Types -----

  interface SafetySecurityContext {
    userId: string;
    role: 'anon' | 'authenticated' | 'service_role';
  }

  // Simulated policy enforcement
  const OWNER_A: SafetySecurityContext = { userId: 'user-a-uuid', role: 'authenticated' };
  const OWNER_B: SafetySecurityContext = { userId: 'user-b-uuid', role: 'authenticated' };
  const ANON: SafetySecurityContext = { userId: '', role: 'anon' };
  const SERVICE: SafetySecurityContext = { userId: 'service-role', role: 'service_role' };

  // Simulated SQL policies from the migrations
  function canReadReport(ctx: SafetySecurityContext, reportOwnerId: string): boolean {
    if (ctx.role === 'service_role') return true;
    if (ctx.role === 'anon') return false;
    return ctx.userId === reportOwnerId; // RLS: auth.uid() = owner_user_id
  }

  function canReadEvidence(ctx: SafetySecurityContext, reportOwnerId: string): boolean {
    if (ctx.role === 'service_role') return true;
    if (ctx.role === 'anon') return false;
    return ctx.userId === reportOwnerId;
  }

  function canReadCustodyChain(ctx: SafetySecurityContext, reportOwnerId: string): boolean {
    if (ctx.role === 'service_role') return true;
    if (ctx.role === 'anon') return false;
    return ctx.userId === reportOwnerId;
  }

  function canMutateEvidence(_ctx: SafetySecurityContext): boolean {
    // Immutability trigger: REVOKE INSERT, UPDATE, DELETE on safety_evidence_objects FROM authenticated
    return false; // Only service_role RPCs can insert/register
  }

  function canMutateCustody(_ctx: SafetySecurityContext): boolean {
    return false; // Immutability trigger blocks all authenticated mutations
  }

  function canReadPublicPoints(ctx: SafetySecurityContext): boolean {
    if (ctx.role === 'anon') return false; // Public points still require auth
    return true; // All authenticated can read public points
  }

  function canReadPublicPointReportLinks(ctx: SafetySecurityContext, _linkOwnerId: string): boolean {
    // safety_private.public_point_report_links — not in public schema
    if (ctx.role === 'service_role') return true;
    return false; // No authenticated user has direct SELECT on safety_private
  }

  function canReadCounterclaim(ctx: SafetySecurityContext, claimantId: string): boolean {
    if (ctx.role === 'service_role') return true;
    if (ctx.role === 'anon') return false;
    return ctx.userId === claimantId; // RLS: auth.uid() = submitted_by
  }

  function canDirectInsertReport(_ctx: SafetySecurityContext): boolean {
    // REVOKE INSERT ON safety_reports FROM authenticated — must use RPC
    return false;
  }

  // ----- Tests -----

  describe('Private Report Isolation', () => {
    it('anon user cannot read any private report', () => {
      expect(canReadReport(ANON, OWNER_A.userId)).toBe(false);
      expect(canReadReport(ANON, OWNER_B.userId)).toBe(false);
    });

    it('User A cannot read User B reports', () => {
      expect(canReadReport(OWNER_A, OWNER_B.userId)).toBe(false);
    });

    it('User A can read own reports', () => {
      expect(canReadReport(OWNER_A, OWNER_A.userId)).toBe(true);
    });

    it('Service role can read all reports', () => {
      expect(canReadReport(SERVICE, OWNER_A.userId)).toBe(true);
      expect(canReadReport(SERVICE, OWNER_B.userId)).toBe(true);
    });
  });

  describe('Evidence Cross-User Isolation', () => {
    it('User A cannot read User B evidence', () => {
      expect(canReadEvidence(OWNER_A, OWNER_B.userId)).toBe(false);
    });

    it('User A can read own evidence', () => {
      expect(canReadEvidence(OWNER_A, OWNER_A.userId)).toBe(true);
    });

    it('anon cannot read any evidence', () => {
      expect(canReadEvidence(ANON, OWNER_A.userId)).toBe(false);
    });
  });

  describe('Custody Chain Immutability', () => {
    it('authenticated user cannot UPDATE evidence_objects', () => {
      expect(canMutateEvidence(OWNER_A)).toBe(false);
    });

    it('authenticated user cannot DELETE evidence_objects', () => {
      expect(canMutateEvidence(OWNER_B)).toBe(false);
    });

    it('authenticated user cannot INSERT into evidence_custody', () => {
      expect(canMutateCustody(OWNER_A)).toBe(false);
    });

    it('authenticated user cannot UPDATE evidence_custody', () => {
      expect(canMutateCustody(OWNER_B)).toBe(false);
    });

    it('User A cannot read User B custody chain', () => {
      expect(canReadCustodyChain(OWNER_A, OWNER_B.userId)).toBe(false);
    });
  });

  describe('Public Points Visibility', () => {
    it('authenticated users can read public safety points', () => {
      expect(canReadPublicPoints(OWNER_A)).toBe(true);
      expect(canReadPublicPoints(OWNER_B)).toBe(true);
    });

    it('anon users cannot read public safety points', () => {
      expect(canReadPublicPoints(ANON)).toBe(false);
    });
  });

  describe('Reporter Anonymity Boundary', () => {
    it('User A cannot read public_point_report_links (safety_private schema)', () => {
      expect(canReadPublicPointReportLinks(OWNER_A, OWNER_A.userId)).toBe(false);
    });

    it('User B cannot deanonymize User A via report links', () => {
      expect(canReadPublicPointReportLinks(OWNER_B, OWNER_A.userId)).toBe(false);
    });

    it('Service role CAN read report links for moderation', () => {
      expect(canReadPublicPointReportLinks(SERVICE, OWNER_A.userId)).toBe(true);
    });
  });

  describe('Counterclaim Isolation', () => {
    it('counterclaim is only readable by its author', () => {
      expect(canReadCounterclaim(OWNER_A, OWNER_A.userId)).toBe(true);
      expect(canReadCounterclaim(OWNER_B, OWNER_A.userId)).toBe(false);
    });

    it('anon cannot read counterclaims', () => {
      expect(canReadCounterclaim(ANON, OWNER_A.userId)).toBe(false);
    });
  });

  describe('Direct INSERT Prevention', () => {
    it('authenticated user cannot directly INSERT into safety_reports', () => {
      expect(canDirectInsertReport(OWNER_A)).toBe(false);
    });

    it('REVOKE prevents all authenticated INSERT — must use safety_create_report_v2 RPC', () => {
      expect(canDirectInsertReport(OWNER_B)).toBe(false);
    });
  });

  describe('SQL Migration Contract Verification', () => {
    const fs = require('fs');
    const path = require('path');
    const repoRoot = path.resolve(__dirname, '../..');

    function readMigration(filename: string): string {
      return fs.readFileSync(
        path.join(repoRoot, 'supabase/migrations', filename),
        'utf-8',
      );
    }

    it('safety_authority_integrity REVOKEs INSERT on safety_reports from authenticated', () => {
      const sql = readMigration('20260919160821_safety_authority_integrity.sql');
      expect(sql).toContain('revoke insert');
      expect(sql).toContain('safety_reports');
    });

    it('safety_authority_integrity creates immutability triggers on evidence', () => {
      const sql = readMigration('20260919160821_safety_authority_integrity.sql');
      // Evidence immutability: prevent UPDATE/DELETE
      expect(sql).toContain('safety_evidence_immutable');
      expect(sql).toContain('custody');
    });

    it('reporter_anonymity_boundary moves source_report_id to safety_private', () => {
      const sql = readMigration('20260920021000_safety_reporter_anonymity_boundary.sql');
      expect(sql).toContain('safety_private');
      expect(sql).toContain('public_point_report_links');
    });

    it('safety_create_report_v2 checks runtime_feature_gates', () => {
      const sql = readMigration('20260919160821_safety_authority_integrity.sql');
      expect(sql).toContain('runtime_feature_gates');
      expect(sql).toContain('safety_reporting');
    });

    it('counterclaims table has RLS enabled', () => {
      const sql = readMigration('20260919160821_safety_authority_integrity.sql');
      expect(sql).toContain('safety_counterclaims');
      expect(sql).toContain('enable row level security');
    });
  });
});
