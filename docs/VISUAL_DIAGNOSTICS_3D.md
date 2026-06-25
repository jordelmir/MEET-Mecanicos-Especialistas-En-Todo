# Diagnostico Visual 3D

El modulo 3D deja de ser una ilustracion aislada y queda conectado a una base tecnica por componente:

- tipo de motor: L4, V6, V8 o EV;
- pieza, categoria, ubicacion fisica y llave de malla 3D;
- DTCs relacionados con severidad y peso;
- PIDs OBD relacionados con rango esperado;
- pruebas de taller, flujo de reparacion, herramientas, especificaciones y seguridad;
- contexto listo para IA con vehiculo, DTCs activos y lecturas vivas.

## Flujo Real

1. La pantalla detecta el tipo de motor desde el vehiculo activo.
2. `VisualDiagnosticRepositoryImpl` carga componentes desde `VisualDiagnosticSeedData`.
3. El visor 3D envia `meshId` al tocar una pieza.
4. `ComponentLocatorScreen` mapea `meshId` a `DiagnosticComponent`.
5. La ficha muestra DTCs activos, PIDs vivos y guia tecnica.
6. Si no hay escaner conectado o no existe lectura, la UI muestra `Sin lectura en vivo`.
7. El boton `ARMAR CONTEXTO IA DE ESTA PIEZA` genera contexto tecnico verificable para la consulta.

## Como Agregar Una Pieza

Agregar un `DiagnosticComponent` en:

```txt
android/app/src/main/kotlin/com/elysium369/meet/data/visualdiagnostics/VisualDiagnosticSeedData.kt
```

Campos minimos:

- `id`: estable y unico, por ejemplo `alternator`;
- `meshKey`: id de la malla 3D, por ejemplo `alternator`;
- `relatedPids`: PIDs OBD que la app puede leer;
- `relatedDtcs`: codigos que deben resaltar la pieza;
- `workshopTests`: pruebas fisicas de taller, no conclusiones simuladas;
- `repairFlow`: pasos de reparacion con confirmacion;
- `safetyWarnings`: riesgos reales antes de intervenir.

## Reglas

- Un DTC nunca confirma una pieza danada por si solo.
- La UI no debe fingir valores OBD; si no hay lectura, se muestra como falta de dato.
- Todo fusible/rele debe incluir amperaje, alimentacion esperada, continuidad, funcion y prueba bajo carga cuando aplique.
- EV/HV debe exigir advertencias de alto voltaje, desenergizacion OEM y confirmacion de ausencia de tension.
- La calidad visual depende del mesh/procedural renderer, pero la verdad diagnostica vive en la ficha tecnica.
