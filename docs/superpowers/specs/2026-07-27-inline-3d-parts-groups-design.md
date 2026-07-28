# MEET — Piezas agrupadas con 3D/360 integrado

Fecha: 2026-07-27  
Estado: aprobado por delegación explícita del propietario  
Vehículo piloto: Hyundai Accent/Verna 2005, 1.6 DOHC, automático

## Objetivo

Convertir `Piezas` en un catálogo técnico ordenado por sistemas y mostrar la
representación 3D/360 directamente dentro de cada ficha, junto al conocimiento
literal y su trazabilidad. La ficha deja de usar el glifo circular como visual
principal cuando existe una experiencia canónica enlazable.

## Enfoques evaluados

1. Mantener la lista plana y abrir el motor 3D en otra pantalla. Es el cambio
   más pequeño, pero conserva el desorden y obliga al usuario a perder contexto.
2. Copiar o regenerar una malla por cada entidad propietaria. Aumenta
   duplicación, deriva visual y riesgo de presentar una aproximación como OEM.
3. Reutilizar los 130 paquetes canónicos existentes, resolver cada entidad
   propietaria contra una experiencia única y mostrarla inline. Este es el
   enfoque seleccionado porque conserva una sola fuente visual, funciona
   offline y mantiene juntos conocimiento, 3D y acciones.

## Arquitectura

### Resolución de identidad

`ProprietaryCanonical3dResolver` construye un índice de los 6.405 elementos de
los atlas G4ED y técnicos. La resolución usa, en orden:

1. nombre o alias normalizado idéntico;
2. huella nominal conservadora que tolera plural y separadores;
3. desempate por familia técnica compatible.

Solo se enlaza cuando el candidato es único. Una coincidencia visual o de texto
no cambia compatibilidad comercial ni autoridad OEM. Si no existe un candidato
único, la ficha informa que el modelo no está vinculado y conserva la
navegación al atlas completo.

### Experiencia 3D inline

La ficha carga el manifest y binding del candidato resuelto y reutiliza el visor
Filament existente:

- órbita táctil y auto 360;
- zoom;
- aislar/contexto;
- despiece/reensamble;
- reset;
- botón para abrir la experiencia completa.

La superficie se rotula `RECONSTRUCCIÓN TÉCNICA DE REFERENCIA`. Se muestran el
nombre canónico, sistema y advertencia de autoridad. Nunca se usan los términos
`CAD OEM`, `geometría exacta` o `compatibilidad exacta` sin evidencia.

### Organización del catálogo

Los sistemas actuales se agrupan sin perder sus filtros originales:

- Motor y alimentación;
- Caja y tren motriz;
- Eléctrico y electrónico;
- Hidráulico, frenos y dirección;
- Chasis, suspensión y ruedas;
- Carrocería y exterior;
- Cabina, confort y seguridad;
- Híbrido/EV, fluidos y hardware.

Cada familia aparece como un bloque seleccionable con cantidad real. Al elegir
una familia se muestran sus subsistemas como chips. En la lista, cada subsistema
tiene encabezado, color y conteo, de modo que `Todos` ya no sea una secuencia
plana de cientos de piezas.

## Estados y errores

- Cargando: panel técnico con progreso explícito.
- Resuelto: visor 3D/360 inline y metadatos canónicos.
- No resuelto: mensaje honesto y acceso al atlas; no se fabrica una identidad.
- Error de asset: se conserva el conocimiento literal y se ofrece reintento o
  vista completa, sin cerrar la app.
- Caso real: no se presenta como pieza vendible ni se fuerza una malla.

## Recomendaciones técnicas incorporadas

- El 3D sirve para comprensión y comparación, no para declarar compatibilidad.
- VIN, OEM, foto, conector y medidas siguen siendo requisitos de confirmación.
- Las variantes condicionales conservan `PENDING_PHYSICAL_CONFIRMATION`.
- El contenido literal, hashes y procedencia permanecen visibles.
- La búsqueda no pierde resultados por la nueva agrupación.
- No se cambian Room, contratos de reportes ni hashes TS/Kotlin.

## Pruebas y aceptación

- `Panel cortafuego / firewall` resuelve de forma única a
  `body-0125-panel-cortafuegos`.
- El resolver rechaza coincidencias ambiguas.
- Las 4.753 entidades siguen accesibles por búsqueda y filtros.
- Los conteos de familias coinciden con el manifest.
- El visor carga, gira y no produce `FATAL EXCEPTION` en el dispositivo.
- Suite Android, paridad TS/Kotlin y `assembleDebug` permanecen verdes.
- La APK se instala, abre y se valida visualmente vía ADB.
