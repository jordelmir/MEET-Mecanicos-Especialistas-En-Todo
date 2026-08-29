# Security & Privacy Model

## 1. Zero Trust & Server Verification
- Client JWT subject is verified server-side. No client-supplied user ID is trusted as authority.
- Service role keys, database credentials, and LiveKit secrets are strictly forbidden in client APKs.

## 2. High-Risk Physical Command Interlocks
- Clear DTC, Actuator Tests, and ECU Writes require: Active physical session + On-screen confirmation + Engine stopped + Vehicle stationary.

## 3. Privacy & Telemetry Masking
- Operational telemetry and public logs never contain raw full VIN, license plate, phone numbers, exact GPS, or chat plaintext.
