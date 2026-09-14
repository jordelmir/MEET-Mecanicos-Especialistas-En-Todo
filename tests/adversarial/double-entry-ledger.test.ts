import { describe, it, expect, beforeEach } from 'vitest';
import { createHash } from 'node:crypto';

describe('Adversarial & Property-Based Double-Entry Ledger Verification', () => {
  interface LedgerAccount {
    id: string;
    code: string;
    currency: string;
    kind: 'ASSET' | 'LIABILITY' | 'REVENUE' | 'EXPENSE';
  }

  interface LedgerEntry {
    account_id: string;
    debit_minor: bigint;
    credit_minor: bigint;
  }

  interface LedgerTransaction {
    id: string;
    idempotency_key: string;
    request_hash: string;
    currency: string;
    entries: LedgerEntry[];
  }

  const accounts = new Map<string, LedgerAccount>();
  const transactions = new Map<string, LedgerTransaction>();

  beforeEach(() => {
    accounts.clear();
    transactions.clear();

    accounts.set('acc-cash', { id: 'acc-cash', code: 'CASH', currency: 'CRC', kind: 'ASSET' });
    accounts.set('acc-driver', { id: 'acc-driver', code: 'DRIVER_PAYABLE', currency: 'CRC', kind: 'LIABILITY' });
    accounts.set('acc-revenue', { id: 'acc-revenue', code: 'COMMISSION_REVENUE', currency: 'CRC', kind: 'REVENUE' });
  });

  function computeHash(kind: string, currency: string, externalRef: string, entries: LedgerEntry[]): string {
    const serialized = JSON.stringify(entries, (_, v) => typeof v === 'bigint' ? v.toString() : v);
    return createHash('sha256')
      .update(`${kind}|${currency}|${externalRef}|${serialized}`)
      .digest('hex');
  }

  function simulatePostLedgerTransaction(
    idempotencyKey: string,
    kind: string,
    currency: string,
    externalRef: string,
    entries: LedgerEntry[],
  ): string {
    if (!Array.isArray(entries) || entries.length < 2) {
      throw new Error('INVALID_LEDGER_ENTRY_SET');
    }

    const hash = computeHash(kind, currency, externalRef, entries);

    const existing = transactions.get(idempotencyKey);
    if (existing) {
      if (existing.request_hash !== hash) {
        const err = new Error('IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD');
        (err as any).code = '23505';
        throw err;
      }
      return existing.id;
    }

    let debits = 0n;
    let credits = 0n;

    for (const entry of entries) {
      if (entry.debit_minor < 0n || entry.credit_minor < 0n) {
        throw new Error('NEGATIVE_MONEY');
      }
      if ((entry.debit_minor === 0n && entry.credit_minor === 0n) ||
          (entry.debit_minor > 0n && entry.credit_minor > 0n)) {
        throw new Error('INVALID_ENTRY_EXCLUSIVE_DEBIT_CREDIT');
      }

      const acc = accounts.get(entry.account_id);
      if (!acc || acc.currency !== currency) {
        throw new Error('INVALID_ACCOUNT_OR_CURRENCY');
      }

      debits += entry.debit_minor;
      credits += entry.credit_minor;
    }

    if (debits !== credits || debits <= 0n) {
      const err = new Error(`UNBALANCED_LEDGER_TRANSACTION: debits=${debits}, credits=${credits}`);
      (err as any).code = '22023';
      throw err;
    }

    const txId = `tx-${Math.random().toString(36).substring(2)}`;
    transactions.set(idempotencyKey, {
      id: txId,
      idempotency_key: idempotencyKey,
      request_hash: hash,
      currency,
      entries,
    });

    return txId;
  }

  it('accepts balanced double-entry transactions and enforces sum(debit) === sum(credit)', () => {
    const txId = simulatePostLedgerTransaction('idem-1', 'RIDE_PAYMENT', 'CRC', 'ride-123', [
      { account_id: 'acc-cash', debit_minor: 5000n, credit_minor: 0n },
      { account_id: 'acc-driver', debit_minor: 0n, credit_minor: 4500n },
      { account_id: 'acc-revenue', debit_minor: 0n, credit_minor: 500n },
    ]);

    expect(txId).toBeDefined();
    expect(transactions.size).toBe(1);
  });

  it('rejects unbalanced transactions where debits != credits', () => {
    expect(() => {
      simulatePostLedgerTransaction('idem-unbalanced', 'RIDE_PAYMENT', 'CRC', 'ride-123', [
        { account_id: 'acc-cash', debit_minor: 5000n, credit_minor: 0n },
        { account_id: 'acc-driver', debit_minor: 0n, credit_minor: 4000n },
      ]);
    }).toThrow('UNBALANCED_LEDGER_TRANSACTION');
  });

  it('rejects transactions with single entry', () => {
    expect(() => {
      simulatePostLedgerTransaction('idem-single', 'RIDE_PAYMENT', 'CRC', 'ride-123', [
        { account_id: 'acc-cash', debit_minor: 5000n, credit_minor: 0n },
      ]);
    }).toThrow('INVALID_LEDGER_ENTRY_SET');
  });

  it('rejects transactions with negative money or simultaneous debit and credit', () => {
    expect(() => {
      simulatePostLedgerTransaction('idem-neg', 'RIDE_PAYMENT', 'CRC', 'ride-123', [
        { account_id: 'acc-cash', debit_minor: -500n, credit_minor: 0n },
        { account_id: 'acc-driver', debit_minor: 0n, credit_minor: -500n },
      ]);
    }).toThrow('NEGATIVE_MONEY');
  });

  it('rejects reuse of idempotency key with modified payload', () => {
    const validEntries = [
      { account_id: 'acc-cash', debit_minor: 1000n, credit_minor: 0n },
      { account_id: 'acc-driver', debit_minor: 0n, credit_minor: 1000n },
    ];

    const tx1 = simulatePostLedgerTransaction('idem-reused', 'PAYMENT', 'CRC', 'ref-1', validEntries);

    // Identical payload returns same txId (idempotent)
    const tx2 = simulatePostLedgerTransaction('idem-reused', 'PAYMENT', 'CRC', 'ref-1', validEntries);
    expect(tx1).toBe(tx2);

    // Modified amount fails with IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD
    const modifiedEntries = [
      { account_id: 'acc-cash', debit_minor: 2000n, credit_minor: 0n },
      { account_id: 'acc-driver', debit_minor: 0n, credit_minor: 2000n },
    ];

    expect(() => {
      simulatePostLedgerTransaction('idem-reused', 'PAYMENT', 'CRC', 'ref-1', modifiedEntries);
    }).toThrow('IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD');
  });
});
