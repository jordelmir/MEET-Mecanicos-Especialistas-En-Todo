# MEET SAFETY CONSTITUTION

> Every invariant below is non-negotiable. If any code path violates one,
> it is a production blocker.

## Invariants

```
HUMAN SAFETY > ENGAGEMENT.

REPORT != FACT.
CLAIM != EVENT.
EVENT != CASE.

HASH INTEGRITY != TRUTH.
AUTHENTICITY != INTERPRETATION.
CORROBORATION != PUBLICATION PERMISSION.

ACCOUNT COUNT != INDEPENDENT SOURCE COUNT.

NO PUBLIC INFORMATION != NO ACTION.

ARREST != GUILT.
INSTITUTIONAL ALLEGATION != ESTABLISHED FACT.

LOCAL INTENT != REMOTE SUCCESS.

NO SILENT EDIT.
NO SECRET DELETION.
NO PUBLICATION WITHOUT PROVENANCE.

NO PRIVATE LOCATION THROUGH PUBLIC PROJECTIONS.

NO ADMIN GOD MODE.
NO CLIENT AUTHORITY OVER VERIFICATION STATE.

EVERY SENSITIVE READ MUST BE MINIMAL AND AUDITABLE.

CORRECTIONS PRESERVE HISTORY.

UNCERTAINTY MUST REMAIN UNCERTAINTY.
```

## Epistemological Model

```text
USER INTENT
    ↓
LOCAL DURABLE INTENT
    ↓
OUTBOX
    ↓
SERVER AUTHORITY
    ↓
AUTHORITATIVE STATE
    ↓
LOCAL PROJECTION
    ↓
UI
```

## Information Flow

```text
PRIVATE INGESTION
    ↓
REPORT
    ↓
CLAIMS
    ↓
EVIDENCE
    ↓
EVENTS
    ↓
CASES
    ↓
TRUST / MODERATION / PRIVACY
    ↓
PUBLICATION FIREWALL
    ↓
PUBLIC PROJECTION
```

## Forbidden Patterns

```text
Report → Map               (NEVER: allegation skips authority)
Citizen allegation → Fact  (NEVER: claim becomes truth automatically)
Client says delivered → Delivered (NEVER: client asserts remote state)
```

## SAFETY AUTHORITY RULE

No Safety UI may promote an allegation, delivery, corroboration,
publication, institutional response, arrest, charge, judgment or
public location unless that state originates from an authoritative
Safety projection.

Local state represents intent only. Intent is never truth.
