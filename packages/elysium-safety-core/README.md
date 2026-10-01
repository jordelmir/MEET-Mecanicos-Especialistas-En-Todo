# Elysium Safety Core

Reusable deterministic contracts for truth states, source independence, custody
hashes, two-person publication eligibility, coarse geospatial areas and offline
routing. No Android UI, OBD, Mobility, Marketplace, customer data or credentials.

The live evidence Edge verifier consumes `sha256Hex` from this package. The
conformance tests exercise real contracts; internal examples are not evidence
of external adoption. Eligibility calculations never replace server authorization.

Run `npm run build` here, then `npm pack --dry-run`. The package is private and
unlicensed pending the owner's explicit Apache-2.0 authorization. Publishing to
NPM/Maven, external adopters and independent review remain separate release gates.

Kotlin sources under `kotlin/` use the same custody wire fixture as TypeScript
and PostgreSQL. `tests/parity/verify-safety-custody.sh` checks all three runtimes.
