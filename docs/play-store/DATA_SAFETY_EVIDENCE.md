# Google Play Store: Data Safety Section Evidence & Declarations

## Application Identity
- **App Name**: MEET — Mecánicos Especialistas En Todo (ELYSIUM Mobility & Services)
- **Package ID**: `com.meet.app` / `com.elysium.vanguard`
- **Audit Target Version**: V11 Public Launch Release

---

## 1. Data Collection & Sharing Declarations

| Data Type | Collected | Shared | Purpose | Ephemeral / Stored | Encryption |
|---|---|---|---|---|---|
| **Approximate Location** | Yes | No | Ride pickup estimation, nearby driver discovery | Stored during session | In transit (TLS 1.3) & At rest (AES-256) |
| **Precise Location** | Yes | Yes (With matched rider during active trip only via safe projection) | Navigation, trip progress, road routing, driver arrival | Stored during active trip; historical trajectory retained for dispute resolution | In transit (TLS 1.3) & At rest (AES-256) |
| **Name** | Yes | Yes (First name only shared with matched counterparty) | Account management, driver/rider identification | Stored; pseudonymized upon account deletion | In transit (TLS 1.3) & At rest (AES-256) |
| **Email Address** | Yes | No | Account authentication, receipts, security notifications | Stored; deleted upon account deletion | In transit (TLS 1.3) & At rest (AES-256) |
| **Phone Number** | Yes | No (Masked VoIP proxy used for trip communications) | Identity verification, two-factor authentication, critical safety alerts | Stored; scrubbed (NULL) upon account deletion | In transit (TLS 1.3) & At rest (AES-256) |
| **User Payment Info** | No | No (Processed directly via PCI-DSS Level 1 compliant PSP: Google Play Billing / Stripe SDK) | In-app purchase & mobility settlement | Zero cardholder data stored on MEET servers | In transit (TLS 1.3) & At rest (AES-256) |
| **Purchase History** | Yes | No | Double-entry ledger, receipt generation, tax compliance | Stored (financial records retained per statutory legal period) | In transit (TLS 1.3) & At rest (AES-256) |
| **Photos & Documents** | Optional | No | Driver license, vehicle registration, mechanical inspection certificates | Stored in private Supabase Storage buckets with signed URL access | In transit (TLS 1.3) & At rest (AES-256) |
| **Device or other IDs** | Yes | No | Fraud prevention, push notifications (FCM token), session security | Stored; updated on login | In transit (TLS 1.3) & At rest (AES-256) |

---

## 2. Security Practices
- **Data Encrypted in Transit**: All communication between the mobile client and backend endpoints uses HTTPS with TLS 1.3.
- **Data Encrypted at Rest**: All databases, storage buckets, and backups use AES-256 encryption.
- **Independent Security Review**: Architecture verified against OWASP Mobile Application Security Verification Standard (MASVS) and PostgreSQL Row Level Security (RLS) policies.
- **Account Deletion Mechanism**: Full automated self-service in-app deletion and web deletion URL compliant with Google Play policy.

---

## 3. Play Store Questionnaire Mapping
- Does your app collect or share any of the required user data types? **Yes**
- Is all user data encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **Yes**
- Deletion URL: `https://meet.app/account/delete`
