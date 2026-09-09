# Google Play Store: Review Credentials & Instructions

## Application Identity
- **App Name**: MEET — Mecánicos Especialistas En Todo
- **Package ID**: `com.elysium369.meet`
- **Version**: 4.23.6 (versionCode 56)
- **Track**: Internal Testing / Closed Testing

---

## 1. Test Account Credentials

### Passenger Account (for ride request flow)
| Field | Value |
|---|---|
| **Email** | `reviewer.passenger@meet-test.com` |
| **Password** | `MeetReview2026!` |
| **Phone** | `+506 8888 0001` |
| **Role** | Passenger (PASSENGER) |
| **Status** | Verified, Active |

### Driver Account (for ride acceptance flow)
| Field | Value |
|---|---|
| **Email** | `reviewer.driver@meet-test.com` |
| **Password** | `MeetReview2026!` |
| **Phone** | `+506 8888 0002` |
| **Role** | Driver (CONDUCTOR) |
| **Status** | Verified, Active, Driver Verification APPROVED |

### Admin Account (for moderation features)
| Field | Value |
|---|---|
| **Email** | `reviewer.admin@meet-test.com` |
| **Password** | `MeetReview2026!` |
| **Phone** | `+506 8888 0003` |
| **Role** | Admin |
| **Status** | Verified, Active |

> **Note**: These are test accounts on the SANDBOX Supabase project. No real money is involved.
> The accounts have pre-populated vehicle data, diagnostic history, and fleet memberships.

---

## 2. Reviewer Walkthrough

### Step 1: Installation & First Launch
1. Install MEET from Google Play Internal/Closed Testing track.
2. Open the app. The home screen shows the vehicle dashboard (if logged in) or onboarding.
3. **No permissions are requested at launch.** Permissions are requested contextually.

### Step 2: Login
1. Tap "Iniciar Sesión" on the onboarding screen.
2. Enter the passenger or driver email and password from the credentials above.
3. The app authenticates via Supabase Auth and loads the user profile.

### Step 3: Passenger Ride Flow
1. Navigate to "Servicios" → "Solicitar Viaje".
2. Enter pickup location and destination.
3. The app searches for nearby drivers.
4. (In test mode, the driver account can manually accept via the driver dashboard.)

### Step 4: Driver Ride Flow
1. Login with the driver account.
2. Navigate to "Servicios" → "Panel de Conductor".
3. Toggle "Disponible" to appear in the dispatch radius.
4. When a ride request arrives, tap "Aceptar".

### Step 5: Chat / UGC Moderation
1. From the home screen, tap "Chat Flota".
2. Open any conversation.
3. Tap the flag icon (🚩) in the top bar to report a user.
4. Select a reason and submit the report.
5. Alternatively, tap the block icon (⛔) to block a user.

### Step 6: Account Deletion
1. Navigate to "Configuración" (Settings).
2. Scroll to the bottom.
3. Tap "ELIMINAR CUENTA".
4. Confirm the deletion dialog.
5. The app calls the server-side deletion RPC and signs out.

### Step 7: OBD Diagnostics (if Bluetooth adapter available)
1. Connect a Bluetooth OBD-II adapter to the vehicle.
2. The app will automatically detect and connect.
3. Navigate to "Diagnóstico" to view live PID data and DTCs.

---

## 3. Location Permission Disclosure

When the app requests location permission (first ride request or driver availability toggle), the following disclosure is shown:

> **"MEET recopila tu ubicación en tiempo real para mostrar tu posición al pasajero/conductor durante viajes activos. La ubicación se usa exclusivamente para la prestación del servicio de transporte y no se comparte con terceros con fines publicitarios."**

This disclosure is displayed BEFORE the system permission dialog appears, as required by Google Play.

---

## 4. Background Location Justification

MEET requests background location access for:
- **Driver**: To continue tracking location during active trips when the app is in the background or screen is locked, ensuring accurate route recording and passenger safety.
- **Passenger**: To show estimated arrival time of the matched driver.

Background location is ONLY active during an active trip. It is automatically deactivated when the trip ends.

---

## 5. Test Environment

- **Backend**: Supabase SANDBOX project (kluumjhzncitjayvvwtj.supabase.co)
- **Database**: All test data is in the SANDBOX environment
- **Payments**: All payment flows are simulated (SINPE topups use test references)
- **Real money**: None involved in test accounts
- **Location**: Test locations are in San José, Costa Rica metropolitan area

---

## 6. Known Limitations in Test

- OBD diagnostics require a physical Bluetooth adapter connected to a vehicle.
- Chat requires two active accounts (use both passenger and driver credentials).
- Ride dispatch may take up to 30 seconds in test mode due to cold start.
- Some premium features (AI diagnosis, advanced reports) require Play Billing purchase.

---

## 7. Contact for Review Issues

If the Google reviewer encounters any issues accessing the app:
- **Email**: reviews@elysium-vanguard.app
- **Emergency**: +506 8888 9999 (response within 2 hours during business hours)

---

*Document maintained for Google Play Store review compliance. Updated: September 2026.*
