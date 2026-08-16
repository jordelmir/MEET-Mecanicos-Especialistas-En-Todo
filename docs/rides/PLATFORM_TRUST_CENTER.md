# Centro de Confianza de Plataforma

**Estado:** implementado en código; requiere aplicar la migración Supabase y
validación física antes de promover a producción.

## Autoridad

La cuenta confirmada `jordelmir@gmail.com` es el único bootstrap de
`PLATFORM_OWNER`. El cliente Android nunca concede autoridad comparando el
correo: consulta `meet_is_platform_owner()` y falla cerrado ante sesión
ausente, denegación o indisponibilidad.

El privilegio queda ligado al UUID autenticado, al correo actual confirmado y
al registro activo de `platform_authorities`. No se distribuye una clave de
servicio dentro del APK.

## Cola unificada

`service_verification_applications` reúne solicitudes de:

- pasajeros;
- choferes de Viajes;
- grúa y asistencia vial;
- mecánicos o talleres;
- repuesteras.
- proveedores de otros servicios físicos, digitales o híbridos.

Los perfiles universales no se consideran operativos solo por registrarse. La
app sincroniza el estado remoto y exige `APPROVED` para exponerlos como
proveedores activos. El alta de chofer alimenta la misma cola mediante un
trigger sobre `ride_driver_vehicles`.

## Decisiones y evidencia

Las decisiones válidas son `APPROVED`, `REJECTED` y `SUSPENDED`; requieren un
motivo de 3 a 500 caracteres. Cada cambio genera un evento append-only con
actor, estado anterior, estado nuevo, motivo y fecha.

El manifiesto SHA-256 demuestra que el expediente revisado corresponde al
capturado, pero no sustituye la inspección del documento. La interfaz desactiva
la aprobación si no existe al menos manifiesto o referencia de licencia. Para
producción masiva se recomienda integrar un proveedor KYC y un bucket privado
con retención, consentimiento y acceso temporal auditado; nunca exponer
documentos de identidad mediante URLs públicas.

## Gates

- pruebas unitarias de política fail-closed;
- prueba contractual backend/Android para impedir autorización local por email;
- RLS y RPC `security definer` con `search_path` vacío;
- pruebas completas de Viajes, lint, ensamblado, paridad TS/Kotlin;
- instalación, apertura, proceso en foreground y crash log en Android físico.
