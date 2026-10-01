# Safety retention policy

Status: proposed engineering policy; owner/legal approval pending. Server policy
controls eligibility; Android cannot decide deletion or legal hold. The initial
90-day engineering threshold is not a statutory period and must be approved or
replaced before deployment. No scheduled deletion is activated by this change.

| Class | Treatment |
|---|---|
| ERASABLE | Eligible private narrative/location removal after age, authorization and hold checks |
| PSEUDONYMIZABLE | Minimize content under the same explicit audited server workflow |
| RETENTION_REQUIRED | Preserve original evidence and provenance pending reviewed policy |
| LEGAL_HOLD | Suspend removal until a separately authorized append-only release |
| PUBLIC_RECORD_REFERENCE | Keep external canonical reference and immutable revision, subject to rights review |
| IMMUTABLE_AUDIT_METADATA | Keep minimal operation/hash/authority metadata; never preserve private narrative in audit |

`safety_classify_retention_v1` and `safety_apply_retention_v1` require authenticated
legal authority and AAL2. A UUID operation receipt is idempotent; changing its
payload is rejected. The operation records the prior digest and policy version.
Removal affects eligible private content; it does not mutate certified byte
receipts/custody or silently delete storage objects. Those need a separate
approved storage retention process, including backups and processor copies.

An active report or linked-evidence hold blocks content removal. Placement and
release are immutable events; the same person cannot release their own hold.
Document correction, dispute and subject-rights decisions alongside the audit.
Never promise that account deletion erases legally retained evidence immediately.
