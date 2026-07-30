# Fase 6 — Alta piloto honesta y elegibilidad del vehículo

## Resultado

El registro local del chofer ya no termina en un callejón sin salida: después
de validar que todas las evidencias existen, Android calcula un manifiesto
SHA-256, agenda una entrega durable y el servidor crea o actualiza el perfil y
vehículo de Viajes.

El acceso provisional no se presenta como revisión documental:

- autoridad: `PILOT_EVIDENCE_ATTESTATION`;
- revisión documental: `UNDER_REVIEW`;
- permiso piloto: 30 días;
- estado visible local: `PILOT_APPROVED`;
- no se ejecuta reconocimiento facial casero;
- no se envían rutas locales ni fotografías mediante el RPC;
- el servidor recibe sólo el hash del manifiesto, datos operativos mínimos y
  una referencia pseudónima del vehículo.

## Gates de negocio

- el conductor declara la capacidad de asientos entre 1 y 16;
- registros antiguos migran con un asiento conservador porque no existe una
  fuente confiable para inferir su capacidad;
- WorkManager exige red, mantiene idempotencia y aplica reintento exponencial;
- perfiles piloto locales preexistentes se vuelven a encolar automáticamente;
- sólo un vehículo queda activo por conductor;
- la elegibilidad se vuelve falsa al expirar el permiso;
- triggers de servidor bloquean una nueva oferta o asignación con vehículo no
  elegible, incluso si un cliente modificado intenta eludir Android;
- un viaje ya asignado puede finalizar aunque el permiso expire durante el
  servicio, evitando abandonar al pasajero a mitad del trayecto.

## Compatibilidad

La columna heredada `verification_status` continúa actuando como interruptor
de despacho para funciones ya publicadas. Nunca debe mostrarse por sí sola
como prueba documental. La autoridad real queda en `verification_method` y el
ciclo de revisión en `document_review_status`.

## Evidencia ejecutable

```text
bash tests/ride/verify-ride-migration.sh
bash tests/ride/verify-ride-android-authority.sh
bash tests/ride/verify-ride-command-authority-postgres.sh
./gradlew --no-daemon --no-parallel --max-workers=3 \
  :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*'
./gradlew --no-daemon --no-parallel --max-workers=3 \
  :app:compileDebugKotlin
```

La integración PostgreSQL verifica alta, replay idempotente, oferta válida,
expiración y denegación posterior. La carrera existente de 100 claims conserva
exactamente un ganador.

## Siguiente cierre

Persistir y mostrar el ACK remoto del alta, integrar proveedor real de
identidad/liveness, capturar vigencias documentales individuales y construir
la cola de revisión humana. Hasta entonces la interfaz debe decir
“acceso piloto / revisión pendiente”, nunca “documentos verificados”.
