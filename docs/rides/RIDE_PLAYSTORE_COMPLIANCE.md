# ELYSIUM MOBILITY OS — GOOGLE PLAY STORE POLICY COMPLIANCE
**Status**: AUTHORITATIVE AUDIT V1  
**Baseline SHA**: `2f3b4535d8fc9c82d4d718217afe73ab28b65a2c`  
**Governing Rule**: *Compliance with Google Play Location and Data Safety policies is an architectural release blocker, not a publication afterthought.*

---

## 1. Android Location Permissions & Disclosures

### Manifest Permissions
```xml
<!-- Foreground location for passenger pickup selection and driver navigation -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Foreground service type location for active driver tracking -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Prominent Disclosure Requirement
Google Play requires a clear, non-dismissible dialog before the runtime permission dialog:
- **Title**: *"Elysium Mobility OS requiere acceso a tu ubicación"*
- **Disclosure Text**: *"Elysium recopila datos de ubicación para calcular tarifas exactas, guiar al conductor hasta tu punto de partida y permitir que compartas tu recorrido en tiempo real por seguridad, incluso cuando la app está minimizada durante un viaje activo."*
- **Acceptance Action**: User taps *"Aceptar y continuar"*, which immediately invokes `ActivityCompat.requestPermissions`.

---

## 2. Foreground Service Location Type Compliance (Android 14 / API 34+)

1. **Declared Service Type**: `android:foregroundServiceType="location"` on `RideLocationTrackingService`.
2. **Notification Prominence**: Ongoing, high-priority notification displaying:
   - Icon: Car / Shield icon.
   - Title: *"Viaje en curso — Seguimiento activo"*
   - Body: *"Elysium está registrando tu ruta de viaje para tu seguridad y liquidación."*
   - Stop action button: Direct tap to pause or cancel.
3. **Driver-Only Constraint**: The foreground service is initiated ONLY when the user is an active assigned driver on an ongoing trip (`DRIVER_EN_ROUTE`, `ARRIVED`, `IN_PROGRESS`). It terminates immediately upon `COMPLETED` or `CANCELLED`.

---

## 3. Google Play Data Safety Form Declarations

| Data Type | Purpose | Ephemeral / Stored | Shared with Third Parties? |
|---|---|---|---|
| **Approximate Location** | Service functionality, routing | Stored (90 days) | Shared only with ride counterparty |
| **Precise Location** | Navigation, metered fare, forensic safety | Stored (90 days encrypted) | Shared only with ride counterparty |
| **User Identifiers** | Account management, authentication | Stored | No |
| **Financial Info (SINPE / Card)** | In-app payment, driver payout | Processed securely | Only with payment processor |
| **Photos / Documents** | Driver background check & Trust Center | Stored in private bucket | No (Internal trust operators only) |
