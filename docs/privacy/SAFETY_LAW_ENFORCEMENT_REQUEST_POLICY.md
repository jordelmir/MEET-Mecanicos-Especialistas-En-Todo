# Law-enforcement and institutional requests

Status: proposed handling procedure; local counsel must approve it before pilot.
Verify the requesting organization, lawful authority, jurisdiction, necessity,
proportionality, requested resources and time scope. Preserve legal references
privately; audit their digests rather than exposing sensitive documents publicly.

No institution receives service-role tokens, database passwords or unrestricted
database access. The integration gateway validates machine token issuer,
audience, expiry, algorithm and signature; binds subject to an active client;
intersects configured scopes and checks resource grants for restricted reads or
audit exports. mTLS, when required, belongs to the trusted gateway configuration,
not an untrusted client-supplied HTTP header.

Document who authorized each export, scope, purpose, expiration and minimization.
Do not enable near-real-time sensitive location access without separate authority,
privacy assessment and resource authorization. A subpoena/request is not a
finding of criminal guilt. Institutional response events require documented
source evidence and separate review/publication, not citizen interpretation.
