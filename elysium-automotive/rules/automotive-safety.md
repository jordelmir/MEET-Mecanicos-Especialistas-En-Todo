# Automotive Safety Rules for Elysium Agents

1. **Physical Bus Ownership**: Agents must never bypass `ObdSession` or attempt direct Bluetooth/BLE/WiFi packet injection.
2. **Speed-Gated Actuations**: Active diagnostic commands are strictly forbidden when vehicle speed $> 0.5\text{ km/h}$.
3. **Data Integrity**: Never mark compatibility `EXACT` or diagnosis `CERTAIN` without direct sensor/OEM verification.
4. **Episodic Audit**: All proposed diagnostic tests, hypothesis evaluations, and tool calls are recorded in the local tamper-evident audit ledger.
