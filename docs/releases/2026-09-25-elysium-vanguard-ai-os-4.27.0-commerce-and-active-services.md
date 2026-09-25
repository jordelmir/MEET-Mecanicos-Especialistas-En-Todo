# Release 4.27.0 — Local Commerce, Triangular Delivery & Active Services Hub

**Fecha:** 2026-09-25  
**Versión:** `4.27.0` | **versionCode:** `61`  
**Room Schema:** `84` (`MIGRATION_83_84`)  
**Supabase Migration:** `20260925120000_commerce_and_delivery_ecosystem.sql`  
**Dispositivos verificados:** Honor VER-N49 (`127.0.0.1:40117`), Xiaomi M2101K6R (`127.0.0.1:37969`)  

---

## 1. Resumen Ejecutivo

La versión **4.27.0** de **Elysium Vanguard AI OS** expande la visión *"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad"* incorporando el ecosistema completo de **Comercio Local & Delivery Triangular** (Pulperías, Minisúper, Sodas y Restaurantes), el **Hub Unificado de Servicios Activos y Finalizados**, y el **Hardening Definitivo de Cancelación Autoritativa de Viajes**.

---

## 2. Novedades y Capacidades Principales

### A. Ecosistema de Comercio Local y Delivery Triangular (Pulperías & Sodas)
- **Categorías integradas:**
  - `PULPERIA_MINISUPER`: Abarrotes, canasta básica, recargas, farmacia básica y productos locales inmediatos.
  - `SODA_RESTAURANTE`: Comida preparada, casados costarricenses, desayunos típicos, bebidas y repostería.
- **Flujo Triangular Certificado:**
  1. *Cliente:* Selecciona comercio, arma la canasta y confirma la orden con desglose (Subtotal + Envío + Comisión + Total CRC).
  2. *Comercio (Pulpería/Soda):* Recibe la orden en tiempo real, confirma disponibilidad física (`ACCEPTED`), y notifica cuando el paquete está listo para retiro (`PREPARING` → `READY_FOR_PICKUP`).
  3. *Repartidor / Mensajero (Courier):* Detecta misiones de despacho disponibles en su radio de cobertura (`COURIER_ASSIGNED`), retira el paquete en el local (`PICKED_UP`), y realiza la entrega en ruta (`EN_ROUTE`).
  4. *Entrega con PIN de Seguridad:* La finalización (`DELIVERED`) requiere la validación del PIN de 4 dígitos proporcionado al cliente, custodiando la custodia física antes de la liberación de fondos.

### B. Hub Unificado de Servicios Activos y Finalizados (`ActiveAndCompletedServicesHubScreen`)
- Pantalla unificada y reactiva accesible desde la navegación principal, el Companion de Voz ("ver servicios activos", "mis servicios finalizados") y accesos directos.
- Soporta tanto la perspectiva del **Usuario/Cliente** como la del **Proveedor/Chofer/Repartidor**.
- **Tres pestañas temáticas integradas:**
  1. **Movilidad & Viajes:** Seguimiento en vivo de chofer asignado, vehículo, mapa, chat, PIN de abordaje y cancelación segura.
  2. **Servicios Técnicos & Mecánicos:** Bids de talleres, inspección Pre-ITV, grúas y certificados de finalización criptográficos.
  3. **Comercio & Delivery:** Estado de pedidos en pulperías/sodas, mapa de ruta del mensajero y verificación por PIN.

### C. Hardening y Resolución Definitiva de Cancelación de Viajes
- Se corrigió el bloqueo en cancelación de viajes activos:
  - Implementación de `cancelRide(requestId, reason, detail, actorRole)` en `ObdViewModel` con resolución dual.
  - Cancelación local inmediata garantizada (`LOCAL_CANCELLED`) en Room para evitar que la UI quede congelada o desfasada.
  - Registro de comando outbox (`RideCommandSyncWorker`) con reintento resiliente para sincronizar la cancelación autoritativa en Supabase PostgreSQL.
  - Limpieza atómica de `active_ride_selections` para asegurar que un viaje cancelado no vuelva a emerger al recargar la aplicación.

### D. Persistencia Local Room v84 & Supabase Autorizado
- **Room Database v84:**
  - Creación de tabla `commerce_orders` con índices compuestos por `customerId`, `merchantId`, `courierId` y `status`.
  - Migración SQL `MIGRATION_83_84` registrada en `AppModule.kt` y `MeetDatabase.kt`.
  - Exportación de esquema `84.json` en `android/app/schemas/`.
- **Supabase Migration (`20260925120000`):**
  - Tablas `commerce_stores`, `commerce_orders` y `commerce_order_events` con RLS estricto.
  - Funciones RPC autoritativas: `commerce_create_order_v1`, `commerce_advance_merchant_status_v1`, `commerce_assign_courier_v1`, `commerce_complete_delivery_pin_v1`.
  - Doble ledger y asientos de custodia para protección antifraude en moneda local (CRC).

---

## 3. Pruebas y Evidencia Física

- **Cross-Runtime Parity:**
  - TypeScript ≡ Kotlin hash verification: **PASS** (`ci-verify.sh`).
  - Canonical pricing parity v2 (CRC GAM): **PASS**.
- **PostgreSQL & Ride Authority Gates:**
  - `verify-ride-android-authority.sh`: **PASS**.
  - `verify-ride-command-authority-postgres.sh`: **PASS** (todas las suites: concurrencia, roles, outbox, liquidación).
- **Despliegue Físico en Dispositivos:**
  - **Honor VER-N49:** Instalación completada (`Performing Streamed Install -> Success`), proceso activo y verificado en pantalla.
  - **Xiaomi M2101K6R:** Instalación completada (`Performing Streamed Install -> Success`), proceso activo y verificado en pantalla.
