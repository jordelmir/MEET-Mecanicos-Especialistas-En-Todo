# Servicios Elysium — unificación y límites de evidencia

Ambas rutas `universal_services` y `elysium_services` abren la misma experiencia cliente/proveedor. Home Classic conserva una sola entrada. El catálogo procede del servidor, sin precios ni ubicaciones de ejemplo. Las solicitudes, propuestas, estados y métricas de la experiencia principal proceden de tablas/RPC autorizados. Las herramientas anteriores siguen accesibles como herramientas avanzadas e indican que su historial local no confirma una operación en línea.

La migración `20260927001000_unified_service_transition_authority` fue aplicada al proyecto MEET. El cliente acepta una oferta registrada; el servidor deriva proveedor/precio/moneda y cobra el 5% una vez. El proveedor inicia; el cliente confirma finalización. Finalización no significa pago capturado. Calificación única/inmutable tras finalización. Actualización/eliminación directa de solicitudes revocadas, incluidas políticas antiguas permisivas.

El puente de saldo descuenta saldo CRC aprobado de la cartera SINPE y acredita la cartera de servicios en una transacción, con clave idempotente y bloqueo. No crea una recarga a partir de una declaración del usuario. FROZEN queda bloqueado; SUSPENDED_LOW_BALANCE puede recargar y recuperar ACTIVE al superar su umbral.

Prueba mantenida: `python3 android/tests/services/run-unified-authority-contract.py --database meet_online_safety_contract_v4_20260926`. Ejecutada con éxito en base desechable y rollback: precio12000→comisión600, reintento sin duplicación, permisos de inicio/finalización, calificación inmutable, transferencia1000, recuperación idempotente, sobreconsumo rechazado, INSERT COMPLETED/UPDATE directo rechazados. Consulta remota confirmó RPC autenticado permitido, anónimo denegado y UPDATE directo denegado.

Compilación inicial de nueva pantalla: exitosa. Compilación/release final y pruebas físicas deben registrarse por artefacto. Las métricas corresponden al flujo canónico, no incluyen como verdad operaciones locales históricas. CRC es la moneda soportada por esta cartera; no se promete conversión global. Compras premium sin integración de tienda real no generan compra pendiente ni permisos.

Pendiente de prueba física end-to-end con cuentas cliente/proveedor verificadas y recarga SINPE real aprobada. No declarar 100% producción basándose solamente en esta prueba SQL o compilación.

Última validación de código: 48 pruebas seleccionadas sin fallos, incluidas dos pruebas de aislamiento premium entre cuentas. La entrega release sigue en preparación hasta firma/R8/escaneo/checksums y GitHub.
