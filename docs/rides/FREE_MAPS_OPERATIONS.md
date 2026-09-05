# Mapas gratuitos y resilientes — Viajes

## Objetivo

Elysium Vanguard usa software y datos abiertos sin convertir servicios públicos
en una promesa comercial falsa. La APK conserva la misma interfaz para un
piloto gratuito y para una futura infraestructura propia.

## Configuración actual

| Capacidad | Piloto gratuito integrado | Ruta de producción sin cambiar la APK |
|---|---|---|
| Renderizado | MapLibre + estilo oscuro de OpenFreeMap | OpenFreeMap autoalojado u otro estilo compatible |
| Búsqueda | Photon público, con caché de consultas end-user | Photon/Pelias propio en `RIDE_GEOCODER_FALLBACK_URL` |
| Ruta vial | OSRM público, con caché breve de mismos puntos | OSRM propio en `RIDE_ROUTER_FALLBACK_URL` |
| Fallo | Datos/ubicaciones siguen visibles; no hay línea falsa | Conmutación al endpoint configurado y telemetría operacional |

Los endpoints de respaldo se dejan vacíos por defecto. Activarlos requiere una
instancia que controle Elysium; no se introduce un segundo servicio público sin
revisar sus condiciones de uso y capacidad.

```properties
# local.properties para un piloto operado por Elysium
RIDE_MAP_STYLE_URL=https://tiles.openfreemap.org/styles/dark
RIDE_MAP_STYLE_FALLBACK_URL=https://tiles.openfreemap.org/styles/liberty
RIDE_GEOCODER_URL=https://photon.komoot.io/api/
RIDE_GEOCODER_FALLBACK_URL=https://maps.elysium.example/photon/api/
RIDE_ROUTER_URL=https://router.project-osrm.org
RIDE_ROUTER_FALLBACK_URL=https://maps.elysium.example/osrm
```

No hay secretos en estas URLs. Un endpoint privado debe protegerse en el
gateway de Elysium y no dentro de la APK.

## Protección de proveedores

- Búsquedas iguales se conservan diez minutos en caché de memoria; rutas con
  los mismos puntos se conservan tres minutos y se etiquetan como caché local.
- Dos fallos consecutivos abren un circuito de recuperación de treinta
  segundos para que un proveedor público no reciba reintentos en tormenta.
- La caché solo guarda respuestas que ya devolvió un proveedor. No fabrica
  direcciones, coordenadas, ETA ni geometría.
- Una ruta inexistente o un proveedor caído deja el mapa con marcadores reales
  y un estado explícito; jamás une inicio/destino mediante una recta ficticia.
- El estilo oscuro de OpenFreeMap tiene un estilo claro de respaldo. Si ambas
  cargas fallan, la pantalla conserva los datos del viaje en modo datos.

## Reglas de uso abierto

OpenStreetMap es la fuente de datos y debe conservar atribución visible. Sus
servidores públicos no ofrecen SLA y no permiten descargar teselas para crear
mapas offline. Nominatim público no se usa como autocompletado: su política lo
prohíbe. Los servidores públicos son para un piloto moderado; la escala
comercial necesita infraestructura operada por Elysium.

Fuentes primarias:

- [Política de teselas OSM](https://operations.osmfoundation.org/policies/tiles/)
- [Política de Nominatim](https://operations.osmfoundation.org/policies/nominatim/)
- [OSRM open source](https://github.com/Project-OSRM/osrm-backend)
- [OpenFreeMap](https://openfreemap.org/)

## Siguiente escalón operativo

1. Provisionar una instancia Linux separada para mapas; no ejecutar la
   extracción/procesamiento de Costa Rica en la Mac de 8 GB de desarrollo.
2. Publicar Photon/Pelias y OSRM detrás de HTTPS, límite por actor, caché y
   panel de salud sin coordenadas ni direcciones en logs.
3. Configurar los dos endpoints de respaldo, hacer pruebas de corte y medir
   p95/error rate antes de desplazar tráfico de piloto.
4. Mantener las atribuciones de OpenStreetMap/OpenFreeMap y la política de
   privacidad de la aplicación actualizadas.

El software es gratuito y autoalojable; cómputo, ancho de banda, soporte y
operación de producción no son gratis y se deben presupuestar antes de prometer
disponibilidad mundial.

## Descarga offline de Costa Rica (diseño aprobado, no implementado todavía)

Es viable ofrecer dentro de Viajes un botón **Descargar Costa Rica** después de
instalar la APK. La ruta gratuita del piloto es generar un paquete vectorial
propio a partir de datos abiertos de OpenStreetMap, publicarlo como artefacto de
versión y administrarlo con MapLibre. Nunca se hará descarga masiva desde los
servidores públicos de teselas de OSM.

El paquete deberá ser versionado, reanudable, verificar SHA-256 antes de
activarse, conservar la versión anterior hasta completar el cambio y permitir
eliminación desde Ajustes. Un motor local de rutas y un índice local de lugares
permitirán mapa, GPS, búsqueda y navegación sin conexión. Tráfico, cierres,
reportes colaborativos y despacho en vivo seguirán requiriendo conexión y una
infraestructura propia; no se presentarán como datos actuales estando offline.

La generación puede automatizarse inicialmente con GitHub Actions y publicarse
en una versión de GitHub. Esto evita licencias comerciales en el piloto, pero
no convierte ancho de banda y operación a escala en recursos ilimitados.

Fuente adicional del motor seleccionado:

- [MapLibre Compose — administración de regiones offline](https://maplibre.org/maplibre-compose/offline/)
