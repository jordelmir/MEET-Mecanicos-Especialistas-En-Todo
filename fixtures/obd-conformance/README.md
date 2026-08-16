# Corpus OBD de conformidad física

Este directorio es la autoridad de entrada para comparaciones MEET contra un
scanner de referencia. No contiene trazas sintéticas presentadas como pruebas
reales. Un caso solo puede cambiar de `PENDING_PHYSICAL_CAPTURE` a `CERTIFIED`
cuando conserva la traza cruda, el resultado del instrumento de referencia y
la identidad verificable del vehículo/adaptador sin exponer VIN o placa.

Protocolos mínimos: CAN 11/29 bit, ISO-TP single/multi-frame, ISO 9141, KWP,
UDS y DoIP. Clases mínimas: gasolina, diésel, HEV, PHEV y BEV. Adaptadores:
ELM económico, ELM de calidad, clase STN/OBDLink, BLE, Wi-Fi y DoIP.

- `hardware-runs/`: manifiestos firmados y capturas de vehículo/ECU/banco real.
- `golden-traces/`: trazas inmutables promovidas después de revisión.
- `manifest.schema.json`: provenance, software, hashes y cadena de custodia.

Invariantes bloqueantes por caso certificado:

- `inventedDtcCount == 0`
- `falseCleanCount == 0`
- `wrongEcuCount == 0`
- `wrongStatusCount == 0`
- `wrongSnapshotOwnerCount == 0`
- `crossVehicleContamination == 0`

Los archivos binarios de captura se guardan fuera de Git cuando contienen
datos sensibles; el manifiesto versionado conserva SHA-256, tamaño, autoridad
y referencia de custodia. Ausencia de captura significa pendiente, nunca PASS.
Un replay puede probar determinismo, pero nunca se etiqueta como evidencia física.
