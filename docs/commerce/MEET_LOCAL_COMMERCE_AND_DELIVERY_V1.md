# MEET LOCAL COMMERCE & TRIANGULAR DELIVERY SPECIFICATION (V1)

> **"Todo en uno. Siempre a más, nunca a menos. Al máximo nivel de la humanidad."**
> — Operating Principle (AGENTS.md)

---

## 1. Vision & Executive Summary

MEET expands its on-demand physical network beyond vehicle mobility and mechanical roadside rescue into **Neighborhood Commerce & Triangular Express Delivery**.

In Costa Rica and Latin America, the primary everyday economic units are:
1. **Pulperías & Minisúpers**: The local corner grocery stores selling staple pantry goods (arroz, frijoles, azúcar, pan fresco, huevos, leche Dos Pinos, bebidas, snacks y artículos de aseo).
2. **Sodas Criollas & Restaurantes Típicos**: Neighborhood diners preparing fresh homemade meals (casados con carne mechada o chuleta, gallo pinto, empanadas arregladas, chicharrones, olla de carne, batidos naturales).

Previously, global delivery apps extracted 25%–35% predatory commissions from small pulperos and sodas, and imposed opaque fee structures on delivery drivers. MEET provides an authoritative, transparent **local-first platform** charging only a fair 5% platform fee with **Dual Escrow Settlement**:
- The **Merchant (Pulpero / Sodero)** receives 100% of their merchandise subtotal upon preparation and handover.
- The **Courier (Chofer / Repartidor de la Red MEET)** receives the delivery transport fare based on transparent per-kilometer distance and time rates.
- The **Client** receives live GPS tracking, exact item transparency in Costa Rican Colones (₡ CRC), and validates delivery with a **4-digit Delivery Security PIN**.

---

## 2. Triangular Workflow Architecture

```
┌──────────────┐          1. Place Order (Subtotal + Delivery Fee)          ┌──────────────┐
│              ├───────────────────────────────────────────────────────────►│  MEET Core   │
│    CLIENT    │◄───────────────────────────────────────────────────────────┤   Platform   │
│  (Comprador) │                 2. Status: PLACED (PIN Generated)          │   & Escrow   │
└──────┬───────┘                                                            └──┬────────┬──┘
       │                                                                       │        │
       │                                         3. Order Dispatched           │        │ 5. Mission Offer
       │                                            to Merchant Store          │        │    to Couriers
       │                                                                       ▼        ▼
       │                                                            ┌────────────┐   ┌────────────┐
       │                                                            │  MERCHANT  │   │  COURIER   │
       │                                                            │ (Pulpería/ │   │ (Repartidor│
       │                                                            │   Soda)    │   │   MEET)    │
       │                                                            └──────┬─────┘   └──────┬─────┘
       │                                                                   │                │
       │                                        4. PREPARING -> READY      │                │ 6. Accepts Mission
       │                                                                   ▼                │    (COURIER_ASSIGNED)
       │                                                            ┌──────────────┐        │
       │                                                            │ Store Handover│◄───────┘
       │                                                            │ (PICKED_UP)  │
       │                                                            └──────┬───────┘
       │                                                                   │ 7. IN_TRANSIT (Live GPS)
       │                                                                   ▼
       │ 8. Arrives at Door (ARRIVED)                               ┌──────────────┐
       │◄───────────────────────────────────────────────────────────┤ Courier at   │
       │                                                            │ Customer Door│
       │ 9. Client reveals 4-digit PIN                              └──────┬───────┘
       ├───────────────────────────────────────────────────────────────────►│
       │                                                                   │ 10. verify_delivery_pin_and_complete()
       ▼                                                                   ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ DUAL ESCROW RELEASE: Merchant Subtotal Released + Courier Delivery Fee Released + SHA-256 Cert Hash │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Schema & Contracts

### 3.1 Room Entity: `CommerceOrderEntity`
Located at `com.elysium369.meet.commerce.data.local.CommerceOrderEntity`:
- `orderId`: String (UUID PK)
- `commerceType`: "PULPERIA" | "SODA_RESTAURANT"
- `merchantId`: String
- `merchantName`: String
- `merchantPhone`: String
- `merchantAddress`: String
- `merchantLat`, `merchantLng`: Double
- `customerId`: String
- `customerName`, `customerPhone`: String
- `deliveryAddress`: String
- `deliveryLat`, `deliveryLng`: Double
- `itemsJson`: String (human-readable list or itemized JSON)
- `itemsSubtotalMinor`: Long (₡ CRC for merchant)
- `deliveryFeeMinor`: Long (₡ CRC for courier transport)
- `totalAmountMinor`: Long (Subtotal + Delivery Fee)
- `currency`: String ("CRC")
- `paymentMethod`: String ("SINPE", "CASH", "CARD")
- `paymentStatus`: String ("PENDING", "ESCROW_HELD", "RELEASED", "REFUNDED")
- `courierId`, `courierName`, `courierPhone`, `courierVehicle`: String?
- `deliveryPin`: String (4-digit security code)
- `status`: String ("PLACED", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "COURIER_ASSIGNED", "IN_TRANSIT", "ARRIVED", "DELIVERED", "CANCELLED")
- `createdAt`, `readyAt`, `pickedUpAt`, `deliveredAt`, `completedAt`: Long?
- `integrityHash`: String? (SHA-256 completion certificate)
- `ratingStars`: Int?, `reviewNotes`: String?

### 3.2 Supabase Authoritative RPC Surface
Migration `supabase/migrations/20260925120000_commerce_and_delivery_ecosystem.sql`:
- `public.place_commerce_order_v1(...)`
- `public.advance_merchant_order_v1(order_id, new_status)`
- `public.assign_courier_mission_v1(order_id, courier_name, courier_phone, courier_vehicle)`
- `public.advance_courier_delivery_v1(order_id, new_status)`
- `public.verify_delivery_pin_and_complete_v1(order_id, entered_pin, rating, review)`
- `public.cancel_commerce_order_v1(order_id, reason)`

---

## 4. Unified Active and Completed Services Screens

### 4.1 "Servicios Activos" Tab
Provides a unified view for both **Clients** and **Providers**:
- **Filter Chips**: `Todos`, `🚗 Viajes`, `🏪 Pulperías & Sodas`, `🔧 Mecánica & Grúas`.
- **For Clients**:
  - Live Ride Cards: Origin/Destination, boarding PIN, driver info, fare in CRC, and **Safe Cancel Button** (with 4-second timeout guarantee).
  - Pulpería & Soda Cards: Progress stepper (`Orden recibida` -> `En preparación` -> `Chofer en camino` -> `En ruta` -> `Chofer en puerta`), delivery PIN display, courier info, breakdown of merchant subtotal vs delivery fee.
  - Technical Services Cards: Specialist status, price offer, category, and direct cancel option.
- **For Providers (Modo Prestador)**:
  - Choferes / Repartidores: View available delivery missions nearby ("Recoger en Pulpería El Sol y entregar a 1.8 km por ₡1,500 CRC"). Step progression: `Recogido en Local` -> `En Ruta` -> `Llegué al Cliente` -> `Verificar PIN y Entregar`.
  - Merchants (Pulperos / Soders): Confirm orders and notify when packages are ready for courier pickup.

### 4.2 "Servicios Finalizados" Tab
- Historical ledger of completed and cancelled trips, orders, and technical services.
- Real-time date and time formatting (Costa Rica locale).
- Exact CRC amounts and payment method.
- **Forensic SHA-256 Integrity Verification Badge**: Displays hash and opens the Forensic Completion Certificate modal with QR minimal payload, escrow release proof, and tamper-evident audit signature.
- **1-Tap Re-order ("Repetir Pedido")**: Allows consumers to re-request their favorite grocery basket or traditional meal in seconds.

---

## 5. Conversational Living Companion Alignment

The 3D Living Companion (Draco, Volt Sparky, Goku SSJ4) strictly adheres to the principle of **human accompaniment**:
1. **Never acts autonomously on irreversible actions**:
   - Asks for pickup location.
   - Asks for destination or desired items.
   - Asks for payment method.
   - Summarizes the order/trip.
   - **Explicitly asks**: *"¿Confirmas publicar este viaje/servicio? Di Sí para confirmar o Cancelar."*
   - Only upon hearing *"Sí"*, *"Confirmo"*, or *"Publicar"* does it enqueue the transaction.
2. **Voice Shortcuts**:
   - *"Pulpería"* / *"Minisúper"* -> opens Active Services Hub with Pulpería store selector.
   - *"Soda"* / *"Restaurante"* -> opens Active Services Hub with Soda store selector.
   - *"Servicios activos"* -> opens Active Services Hub on Tab 0.
   - *"Servicios finalizados"* / *"Historial"* -> opens Active Services Hub on Tab 1.

---

## 6. Ride Cancellation Safety Guarantee

### Root Cause Diagnosis & Resolution
- **Issue**: Rides in `PENDING_PUBLICATION` with `serverVersion == 0L` caused `RideCommandSyncWorker` to retry `NotFound` endlessly, leaving the cancellation dialog locked indefinitely.
- **Fix Applied**:
  - `RideCommandSyncWorker` now detects `type == CANCEL && expectedVersion == 0L` and clears the pending outbox command, marks the local Room entity as `CANCELLED`, clears `active_ride_selections`, and acknowledges the outbox item.
  - `RideSafetyPanels.kt` sets a strict 4-second timeout on `isProcessingCancel`, automatically resetting on error, and ensures the "VOLVER" button is always enabled and clickable.
  - `ObdViewModel.kt` `canSelectRide` rejects terminal statuses (`COMPLETED`, `CANCELLED`, `EXPIRED`, `VOIDED`).
  - Added `clearAllStuckRides()` to allow instant emergency state reconciliation.
