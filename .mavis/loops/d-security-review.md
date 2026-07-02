# Loop D — Security Review

## Frecuencia

- Semanal
- Antes de release
- En cada cambio de: auth, DB, API, OBD, pagos, storage móvil o MCP

## Comandos automatizados

```bash
gitleaks detect --source . || true
semgrep scan . || true
trivy fs . || true
osv-scanner -r . || true
```

> Durante la fase inicial corren con `continue-on-error: true`. Cuando el repo madure, deben bloquear PR.

## Revisión manual obligatoria

- AuthN / AuthZ
- RLS (Supabase)
- Service role
- Secrets
- SQL injection
- XSS
- SSRF
- Logs sensibles
- Mobile storage
- MCP tool permissions
- Supply chain (deps)
- Destructive actions

## Output — Security Loop Report

```markdown
## Security Loop Report

- Critical:
- High:
- Medium:
- Low:
- False positives:
- Required fixes:
- PR/Issue created:
```

## Prioridad de fixes

1. Critical → fix inmediato, mismo PR si es posible
2. High → PR dedicado en < 24h
3. Medium → PR o issue programado
4. Low → issue programado

## Regla específica MEET (automotriz)

- **VIN / GPS nunca en logs** — test de no-leak es prioridad alta
- Crash telemetry debe sanitizar VIN antes de enviar a servicio externo
- BLE pairing secrets no deben persistir más allá de sesión

## Comando reusable

```
Ejecuta Security Review Loop.

Debes:
1. Inspeccionar repo.
2. Ejecutar gitleaks/semgrep/trivy/osv si están disponibles.
3. Revisar manualmente:
   - auth
   - authorization
   - RLS
   - secrets
   - SQL injection
   - logs sensibles
   - mobile storage
   - MCP config
   - CI permissions
   - dependencies
4. Clasificar hallazgos por severidad.
5. Corregir solo el hallazgo de mayor impacto con menor riesgo.
6. Agregar test de seguridad si aplica.
7. Ejecutar quality gates.
8. Actualizar .mavis/memory/known-risks.md.
9. Crear commit y PR.
10. No hacer merge.
```