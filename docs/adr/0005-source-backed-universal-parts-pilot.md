# ADR 0005: pack fuente para el piloto universal de piezas

**Estado:** aceptado  
**Fecha:** 2026-07-16

## Contexto

MEET necesita unir catalogo, compatibilidad, procedimientos y 3D en web y Android. Los DOCX disponibles contienen una taxonomia extensa, pero sus valores tecnicos no tienen autoridad suficiente para publicacion automatica. El catalogo web previo duplicaba 50 seeds con OEM y torques no trazados. Android ya usa Room v42 y el repositorio tiene trabajo paralelo activo.

## Opciones consideradas

1. Mantener seeds separados en TypeScript y Kotlin. Rapido, pero divergente e imposible de auditar de forma central.
2. Crear nuevas tablas Room/Supabase de inmediato. Escalable, pero añade migraciones antes de validar el contrato y la vertical.
3. Generar un pack JSON compartido, revision-only, y añadir persistencia definitiva despues de validar la vertical.

## Decision

Se adopta la opcion 3.

- Un generador determinista enlaza un glosario curado con bloques reales extraidos de los DOCX.
- El mismo pack se consume en web y Android.
- Todo valor tecnico no verificado queda nulo y se muestra como no confirmado.
- La compatibilidad inicial exige verificacion; nunca es `EXACT` por inferencia.
- La primera escena 3D de suspension se identifica como esquema generico.
- El progreso se guarda con almacenamiento local versionado y `SharedPreferences` para evitar una migracion Room prematura.

## Consecuencias

Positivas:

- contrato auditable y estable entre runtimes;
- eliminacion de afirmaciones tecnicas inventadas en la ruta nueva;
- integracion incremental con bajo riesgo de migracion;
- base preparada para firma, descarga y rollback de packs.

Costes:

- la primera version no puede ofrecer torques ni OEM como hechos;
- una revision tecnica posterior debe promover claims individualmente;
- el modelo 3D es didactico, no dimensional ni OEM.

## Disparadores de revision

Revisar este ADR cuando exista al menos una de estas condiciones:

- fuente OEM/licenciada con claims verificables;
- necesidad probada de sincronizar progreso entre dispositivos;
- pack firmado remoto con rollback operativo;
- mallas 3D con licencia y alcance de variante demostrables.

