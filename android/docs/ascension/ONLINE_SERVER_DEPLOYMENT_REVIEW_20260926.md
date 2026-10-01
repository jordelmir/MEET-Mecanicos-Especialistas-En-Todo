# Contratos de servidor para APK — revisión de despliegue

Proyecto identificado mediante conector de sólo lectura: MEET (`kluumjhzncitjayvvwtj`), ACTIVE_HEALTHY. No se modifica la web.

## Cambios propuestos

1. Mensajes: directorio de claves limitado a miembros aceptados, publicación cifrada mediante RPC idempotente, secuencia serializada por conversación, invitación QR de cinco minutos, vinculación transaccional y proyección de contactos. Se revoca INSERT directo a eventos y escritura directa a dispositivos; funciones autorizadas siguen operando. No hay lectura/entrega inventadas. No existe forward secrecy ni transferencia histórica automática de claves.
2. Safety: RPC v3 que conserva procedencia de ubicación y demografía declarada nullable, validación coherente, hash servidor e idempotencia. Campos exactos privados; no publica coordenadas ni convierte declaraciones en víctimas documentadas. V2 permanece para clientes anteriores.

## Evidencia antes del despliegue

- Funciones nuevas ausentes del servidor al consultar metadata en esta ronda. Tablas base y secuencia identity de comunicaciones presentes. Safety v2/private content presentes.
- Replay íntegro de Communications core + identity + nueva migración en base PostgreSQL local desechable. QR, dos cuentas, tercero no autorizado, bloqueos, claves inmutables y replay idempotente pasan.
- Carrera de dos transacciones: publicación posterior espera a la anterior y obtiene secuencia superior; no se omite el primer evento con cursor.
- Safety fixture + v3: pin privado/procedencia/demografía, replay exacto y rechazo de incoherencia o cambio idempotente pasan. El fixture no equivale a replay íntegro de toda la historia de Safety.
- Contratos locales: `android/tests/communications/online_pairing_contract.sql`, `online_delivery_contract.sql`, `android/tests/safety/report_provenance_contract.sql`.

## Límites y operación

El APK requiere desplegar estos contratos para QR y envío online nuevos. Sin ellos conserva la cola y muestra pendiente. Instalar el APK no despliega SQL. No cambiar gates globales ni publicar casos Safety durante el despliegue. Después, verificar RPC/grants/RLS y recorrido real entre dos cuentas, además de pruebas criptográficas Android. No ejecutar tests destructivos sobre usuarios reales.

3. Evidence Vault: subida de originales limitada por propietario del reporte, ruta canónica y gates antes de upload; bucket privado de 20 MiB; registro serializado con cinco adjuntos, metadata de tamaño/MIME y replay compatible. Compatible con `owner_id` actual y `owner` legado. Android storage-kt 2.2.3 transmite octet-stream, por lo que se acepta ese MIME de transporte; tipo de contenido declarado no equivale a inspección de magic bytes. Se conserva el hash de custodia V1 ya existente.

Prueba adicional PASS en base desechable `meet_evidence_contract_v2_20260926`: propietario moderno owner_id sin owner, legacy owner, gates, cuota, replay y metadata. La carrera de cuota comprobada por el subagente permite exactamente cinco adjuntos. Ningún cluster de pruebas es producción.

## Hashes exactos


El historial y diffs actuales deben revisarse de nuevo antes de aplicar si cambia algún archivo. Esta propuesta no incluye migraciones ajenas, funciones vacías, despliegue web ni publicación GitHub.

- `supabase/migrations/20260926180000_communication_online_pairing.sql` — SHA-256 `ee5076521ce970906939a9fcbfe336ce11a79c6724c5f18280b3f2b7ec04360e`

- `supabase/migrations/20260926181000_safety_report_provenance_v3.sql` — SHA-256 `2756bbdf29a27a5423ac22e9deef72c9104af48b5e05cec165bc24bc671df9af`

- `supabase/migrations/20260926182000_safety_evidence_storage_authority.sql` — SHA-256 `9d2b13b1ae6027414e4dc50b13efeb9d0e2cdf35e702a79229d8c1c6d32161b0`
# Corrección adicional de saldo, preparada y pendiente de autorización

20260926183000_wallet_credit_helper_service_role_only.sql, SHA-256 d97f216d82023911b42d82a45fb1efc3e4cf437b224551574ab49607bea9a606. La inspección de pg_proc del servidor activo confirmó que authenticated tiene EXECUTE sobre ride_driver_wallet_credit_v1 y que el cuerpo no verifica dueño ni aprobación. La migración anterior revocaba PUBLIC/anon, pero conservaba el grant explícito authenticated. La corrección revoca los tres y mantiene service_role; no mueve dinero ni lee datos de clientes.

Prueba local aislada meet_wallet_privilege_20260926: usuario normal y anónimo no pueden acreditar; wrapper SECURITY DEFINER autorizado sigue funcionando; service_role conserva EXECUTE. Transacción ROLLBACK. La conciliación SINPE llama el helper dentro de funciones SECURITY DEFINER y conserva privilegios del dueño. Aplicar únicamente tras confirmación explícita solicitada por AUTONOMY_CHARTER.md.
