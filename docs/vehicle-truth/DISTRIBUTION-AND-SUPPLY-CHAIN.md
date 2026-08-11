# Distribution, performance and supply-chain gates

MEET ships APK and AAB as separately verified artifacts. CI must generate both from the same commit,
record SHA-256 and byte size, validate the signed AAB container and enforce reviewed size budgets.
Budgets never justify deleting diagnostic knowledge, evidence, parity contracts or integrated features.

The release manifest is the handoff source of truth. A production release additionally requires:

1. a non-ephemeral protected signing key;
2. dependency review and repository dependency graph;
3. source/history/binary secret scanning;
4. pinned CI actions and a recorded JDK/Gradle/AGP toolchain;
5. an SBOM from the resolved release dependency graph;
6. signed provenance/attestation from the protected release workflow;
7. measured cold/warm startup, frame timing and memory on declared hardware tiers.

No benchmark or supply-chain result is claimed by this document. Missing measurements remain
`PENDING_EXTERNAL_EXECUTION` until CI or the performance lab produces the corresponding artifact.
`tools/vehicle-truth/compare-reproducible-builds.sh` is the byte-identity experiment; it fails when
two independently produced artifacts differ and never upgrades a non-identical build to reproducible.
