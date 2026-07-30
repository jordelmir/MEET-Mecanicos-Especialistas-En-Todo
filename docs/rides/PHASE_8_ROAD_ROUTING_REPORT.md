# Fase 8 — Corte vial real sin rutas falsas

## Resultado

La pantalla de pasajero y el viaje activo consumen geometría vial real,
distancia y duración mediante `RideRoutingProvider`. El adaptador piloto usa
OSRM `route/v1/driving`, `overview=full` y `geometries=geojson`.

La antigua unión automática:

`recogida → paradas → destino`

fue eliminada de `RideMapStateFactory`. Esos puntos siguen siendo marcadores,
pero sólo una geometría producida por el proveedor puede dibujarse como ruta.

## Experiencia

- el pasajero ve cálculo en progreso, kilómetros, minutos y atribución;
- las paradas resueltas forman parte del orden enviado al router;
- el viaje activo vuelve a calcular la geometría para pasajero y conductor;
- el ETA colaborativo parte de distancia/duración vial cuando están
  disponibles;
- una respuesta `NoRoute`, HTTP fallido o geometría inválida muestra un estado
  explícito;
- sin ruta no se inventa distancia, duración ni línea visual;
- solicitar el viaje sigue siendo posible porque la tarifa es una oferta del
  pasajero, pero las métricas quedan en cero/pending en lugar de ser falsas.

## Operación

- `RIDE_ROUTER_URL` permite reemplazar OSRM sin tocar el dominio;
- el servidor demostrativo por defecto sirve sólo para piloto y desarrollo;
- el lanzamiento requiere instancia propia o proveedor con SLA;
- MapLibre y Photon conservan configuración independiente;
- atribución de OpenStreetMap se muestra junto a la ruta.

## Evidencia

```text
curl https://router.project-osrm.org/route/v1/driving/...
Ok points=96 distance_m=3441.1 duration_s=391.7

./gradlew --no-daemon --no-parallel --max-workers=3 \
  :app:testDebugUnitTest --tests 'com.elysium369.meet.ride.*' \
  :app:compileDebugKotlin
BUILD SUCCESSFUL
```

Las pruebas cubren parsing GeoJSON, métricas y la regla de que `NoRoute` nunca
se convierte en una línea recta.
