# MEET 4.18.1 — Roles autenticados de Viajes

**Estado:** implementado en código y cubierto por pruebas locales; despliegue y
prueba física se registran por separado.

## Resultado

El paso **¿Cómo usarás Elysium Vanguard?** incorpora dos perfiles de primera
clase:

- **Usuario de viajes** (`ride_passenger`): intención de solicitar viajes.
- **Conductor** (`ride_driver`): intención de ofrecer viajes, sujeta a revisión
  independiente de identidad, documentación y vehículo.

Elegir **Conductor** nunca establece `APPROVED`, nunca crea un vehículo
verificado y nunca concede elegibilidad de despacho. La autoridad de despacho
existente continúa exigiendo sus gates de verificación.

## Flujo cerrado

1. Android guarda el identificador estable seleccionado y marca la
   sincronización como pendiente.
2. Después de autenticarse, Android llama a
   `meet_activate_usage_profile_v1` con la sesión Supabase actual.
3. La RPC obtiene el actor exclusivamente de `auth.uid()`, actualiza
   `user_profiles`, agrega el rol en `user_roles` y reconcilia
   `ride_profiles.mobility_role`.
4. Si una misma persona activa pasajero y conductor, la movilidad resultante
   es `BOTH`; ningún rol previo se elimina.
5. Cada activación deja un evento idempotente y visible solo para su dueño.

## Seguridad y evidencia

- RPC `SECURITY DEFINER` con `search_path` vacío.
- Ejecución revocada a `public` y `anon`; solo `authenticated` puede invocarla.
- Tabla de auditoría con RLS y lectura exclusiva del actor.
- Prueba PostgreSQL real demuestra pasajero → conductor → `BOTH`, replay
  idempotente, cero proveedor/vehículo autoaprobado y aislamiento entre cuentas.
- Pruebas Kotlin cubren IDs estables, etiquetas, mapeo de rol y obligación de
  verificación.

## Pendiente de evidencia física

La experiencia debe instalarse y recorrerse en Android real cuando el Honor
esté nuevamente disponible. No se usó ni se autoriza emulador como sustituto.
