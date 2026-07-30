# Fase 1 — Money, estados, errores y dominio

## Estado

Implementada en el dominio Kotlin puro. La migración de los importes legacy de
Room (`REAL`) a unidades menores (`INTEGER`) queda explícitamente encadenada a
la fase de read model/outbox; no se declara todavía el gate global “cero Double
monetario”.

## Cambios

- `AmountMinor` no negativo.
- `BasisPoints` restringido a 0–10.000.
- comisión pública centralizada en 500 bps, versión
  `ride-commission-v1`;
- cálculo half-up con protección de overflow;
- base comisionable con componentes aprobados, exclusiones y reducciones;
- fixture canónico ₡4.600 → ₡230, sin comisión sobre propina, peaje o promo;
- estados canónicos sin `SAFETY_HOLD`;
- hold de seguridad modelado como metadata operacional;
- errores de transición con códigos estables;
- `RideCommandEnvelope` con ride ID, versión esperada, idempotency key, tipo y
  versión de payload;
- asignación directa limitada a sistema/despachador;
- conductor presenta el PIN al backend antes de abordar/iniciar;
- perfil Gradle ajustado a 3 GB y 3 workers, sin daemon persistente.

## Invariantes demostradas

1. Un importe negativo no entra al dominio.
2. No se mezclan monedas mediante operaciones de `RideMoney`.
3. El valor comercial 500 no se dispersa entre fórmulas.
4. Exclusiones no aumentan la comisión.
5. Reducciones superiores al ingreso dejan base cero, nunca negativa.
6. Overflow falla explícitamente.
7. Una versión obsoleta no muta el viaje.
8. Un estado terminal no se reactiva.
9. El PIN es obligatorio para `PASSENGER_ONBOARD`.
10. Seguridad no altera silenciosamente la máquina de estados.

## Evidencia TDD

Rojo:

- `RideCommissionPolicyTest`: símbolos financieros inexistentes.
- `RideLifecycleTest` y `RideCommandEnvelopeTest`: command envelope, errores,
  versión y estados aún inexistentes.

Verde:

```text
./gradlew --no-daemon --no-parallel :app:testDebugUnitTest \
  --tests com.elysium369.meet.ride.domain.RideLifecycleTest \
  --tests com.elysium369.meet.ride.domain.RideCommandEnvelopeTest \
  --tests com.elysium369.meet.ride.domain.RideCommissionPolicyTest \
  --tests com.elysium369.meet.ride.domain.RideMoneyTest \
  --tests com.elysium369.meet.ride.domain.RideCancellationTest
```

Resultado: `BUILD SUCCESSFUL`.

## Deuda que no se oculta

- Room aún contiene `priceOffer`, `finalPrice` y `counterPrice` como `REAL`.
- La pantalla y el ViewModel heredados todavía usan `Double` para tarifas.
- Android aún no consume la Command API Supabase.
- No existe todavía ledger de doble entrada.
- Los SQL tests ejecutables requieren el runner PostgreSQL de CI.

Estas deudas son entradas obligatorias de las fases 2–4 y bloquean activar
comisión productiva.
