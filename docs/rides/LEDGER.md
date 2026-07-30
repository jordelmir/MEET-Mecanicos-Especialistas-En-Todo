# Ledger financiero de Viajes

## Modelo

El ledger nuevo es append-only y de doble entrada:

- `ride_ledger_transactions`: identidad, evento, idempotencia, viaje, moneda,
  política de comisión y referencia de reverso;
- `ride_ledger_postings`: cuenta, propietario, débito/crédito e importe;
- `ride_commission_calculations`: evidencia reproducible de la fórmula;
- `ride_revenue_split_rule_sets` y `ride_revenue_split_rules`: contratos
  versionados cuya suma es exactamente 500 bps.

Un constraint trigger diferido exige al cierre de la transacción:

1. dos o más postings;
2. una sola moneda, igual a la del journal;
3. suma de débitos igual a suma de créditos.

No se conceden escrituras directas a `anon` ni `authenticated`. Los journals,
postings, cálculos y contratos tienen trigger de inmutabilidad. Una corrección
crea un journal `REVERSAL` que referencia al original.

## Compatibilidad sin big bang

`ride_wallet_ledger` permanece temporalmente como proyección compatible. Un
trigger `AFTER INSERT` crea el journal balanceado dentro de la misma transacción:

| Entrada legacy | Débito | Crédito |
|---|---|---|
| PROMOTIONAL_GRANT | PLATFORM_PROMOTION_EXPENSE | DRIVER_AVAILABLE |
| TOP_UP_CONFIRMED | PAYMENT_CLEARING | DRIVER_AVAILABLE |
| COMMISSION_RESERVED | DRIVER_AVAILABLE | DRIVER_RESERVED |
| COMMISSION_CAPTURED | DRIVER_RESERVED | PLATFORM_COMMISSION_REVENUE |
| COMMISSION_RELEASED | DRIVER_RESERVED | DRIVER_AVAILABLE |
| REFUND | REFUND_CLEARING | DRIVER_AVAILABLE |

El backfill usa `source_entry_id` y `source_entry_type`; es idempotente y no
borra ni modifica entradas previas. Las entradas legacy no contienen una base
comisionable verificable, por lo que se etiquetan `legacy-flat-fare-v0` y dejan
esa base en `NULL` en vez de inventarla.

## Reparto

El rule set inicial de Costa Rica asigna los 500 bps a plataforma. No se crean
porcentajes de central, cooperativa ni referido sin contrato real. Nuevos
contratos se insertan como otra versión efectiva; nunca reescriben historia.

En Kotlin, la distribución usa largest remainder determinista: la suma de
asignaciones siempre coincide con la comisión pública, incluso para importes
pequeños.

## Activación

La doble entrada puede auditarse desde la migración, pero la comisión productiva
permanece desactivada hasta que:

- los tests PostgreSQL ejecutables estén verdes;
- los RPC finales guarden `ride_commission_calculations`;
- RLS/funciones de lectura se prueben por actor;
- la reconciliación confirme saldo legacy = saldo de postings.
