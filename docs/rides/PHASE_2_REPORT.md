# Fase 2 — Ledger y comisión del 5%

## Estado

Implementada la fundación de doble entrada en Kotlin y PostgreSQL. La migración
es aditiva y mantiene el wallet anterior como proyección compatible. Después
de este informe inicial se habilitó PostgreSQL 18 local y la migración ya fue
ejecutada dentro del runner efímero de la fase 3.

## Cambios

- journal inmutable con dos o más postings;
- cuentas lógicas de conductor, plataforma, tenant, cooperativa, referido,
  clearing, disputa y procesador;
- igualdad obligatoria entre débitos y créditos;
- una moneda por journal;
- reverso explícito con referencia al original;
- reserva, liberación y captura de comisión;
- reparto determinista cuya suma contractual es exactamente 500 bps;
- tablas PostgreSQL de transactions, postings, cálculos y reglas;
- constraint triggers diferidos para balance y suma de splits;
- RLS activa y cero permisos directos para `anon`/`authenticated`;
- triggers de inmutabilidad;
- espejo atómico desde `ride_wallet_ledger`;
- backfill idempotente con `source_entry_id`;
- rule set CR de plataforma 500 bps, sin inventar reparto a terceros.

## Evidencia TDD

Rojo:

- `RideDoubleEntryLedgerTest` falló por ausencia de journals, postings,
  cuentas, splits y reversos.

Verde:

```text
./gradlew --no-daemon --no-parallel :app:testDebugUnitTest \
  --tests 'com.elysium369.meet.ride.*'
bash tests/ride/verify-ride-migration.sh
bash tests/parity/ci-verify.sh
```

Resultados:

- suite Kotlin de Viajes: `BUILD SUCCESSFUL`;
- contrato estático de migraciones: `PASS`;
- paridad TypeScript/Kotlin: `OK`.

## Evidencia SQL posterior

`tests/ride/verify-ride-command-authority-postgres.sh` levanta un cluster
PostgreSQL desechable, aplica fundación, inteligencia vial, ledger y autoridad
de comandos, y comprueba journals balanceados. Falta repetir la evidencia en
CI/Supabase staging; no se afirma despliegue productivo.

## Siguiente dependencia

Fase 3 debe:

1. incorporar `expected_version` y errores estables en todos los RPC;
2. guardar `ride_commission_calculations` al reservar y completar;
3. capturar una base final por componentes, no solo tarifa plana;
4. aplicar reglas por tenant/jurisdicción;
5. añadir tests PostgreSQL de idempotencia, RLS y 100 reclamantes.
