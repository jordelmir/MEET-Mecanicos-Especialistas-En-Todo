# Safety Threat Model

## Adversary Classes

| Adversary | Capability | Mitigation |
|---|---|---|
| Fake reporter | Create false reports | Rate limiting, account verification, source independence |
| Coordinated accounts | Bulk false accusations | Independent source clustering, Sybil resistance |
| Bot farm | Automated report flooding | Rate limiting, device attestation, CAPTCHA |
| Stolen account | Access real user's session | Step-up auth for sensitive ops, session binding |
| Stolen phone | Physical access to device | Device lock required, encrypted payloads, remote wipe |
| Malicious moderator | Abuse review access | Audit trail, quorum for sensitive actions, reason codes |
| Compromised admin | Privilege escalation | No admin god mode, step-up auth, audit |
| Database exfiltration | Read all data | Encrypted payloads, RLS, separate trust domains |
| GPS spoofing | False location data | Accuracy field, corroboration required |
| Deepfake | Fabricated evidence | Hash verification, chain of custody, provenance |
| Tampered evidence | Modified files | SHA-256 verification, original ≠ derived |
| Source laundering | Hide true source | Source cluster analysis, independence verification |
| Ransomware | Encrypt/demand | Backup strategy, crypto-shredding capability |
| Insider threat | Access sensitive data | Minimal access, audit, reason codes |
| Network partition | Split brain | Offline-first with server reconciliation |
| Replayed command | Duplicate execution | Idempotency keys, dedup table |
| Duplicated command | Concurrent duplicates | Atomic idempotency reservation |
| Out-of-order events | Stale data applied | Version vectors, serverVersion > localVersion |

## Per-Threat Mapping

Each major threat maps to:
- **Prevent**: mechanism to block before occurrence
- **Detect**: mechanism to identify when it happens
- **Recover**: mechanism to restore correct state
- **Audit**: mechanism to trace what happened
