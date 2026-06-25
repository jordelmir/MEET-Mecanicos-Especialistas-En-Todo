# Google Play Billing y Entitlements

La app debe publicarse inicialmente gratis y monetizar con productos dentro de la app. La integración Android usa Play Billing Library 9.x y el backend verifica tokens antes de activar PRO.

## Productos recomendados en Play Console

Compras únicas:

- `pro_lifetime`
- `gauge_pack_elite`
- `report_pack`
- `gauge_tier_1` a `gauge_tier_10`

Suscripciones:

- `pro_monthly`
- `pro_yearly`
- `workshop_monthly`

## Flujo técnico

1. Android consulta productos con Google Play Billing.
2. La UI muestra precios oficiales de Google.
3. Android lanza `BillingFlow`.
4. Google devuelve `purchaseToken`.
5. Android envía `productId`, `productType` y `purchaseToken` a `verify-google-play-purchase`.
6. Supabase Edge Function consulta Google Play Developer API.
7. El backend guarda recibo con hash del token y activa `user_entitlements`.
8. La app consulta entitlements para habilitar PRO, gauges, reportes o taller.

## Variables requeridas en Supabase Edge Function

```txt
GOOGLE_PLAY_PACKAGE_NAME=com.elysium369.meet
GOOGLE_SERVICE_ACCOUNT_EMAIL=...
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY=...
SUPABASE_URL=...
SUPABASE_SERVICE_ROLE_KEY=...
SUPABASE_ANON_KEY=...
```

## Fuentes oficiales cotejadas

- Google Play Billing Library 9.1.0 fue publicada el 18 de junio de 2026.
- Google exige usar versiones no deprecadas; para el 31 de agosto de 2026 las apps nuevas/updates deben usar Billing Library 8 o superior.
- La verificación de compras debe hacerse con backend usando Google Play Developer API, no solo confiando en el cliente.

