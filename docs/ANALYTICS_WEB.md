# Analytics Web MEET

La web usa una sola API en `src/analytics/analyticsClient.ts`. Los componentes no insertan directo en Supabase; todo pasa por `analytics.track(...)` y la cola offline.

## Tabla

La migración `supabase/migrations/20260625111500_analytics_and_entitlements.sql` crea `analytics_events` con RLS. Clientes anon/autenticados solo insertan. Lectura queda reservada para `service_role` o consultas administrativas.

## Privacidad

El cliente genera `anonymous_id` persistente y `session_id` por sesión. El sanitizador elimina campos sensibles como email, teléfono, VIN completo, cédula, direcciones y tokens antes de encolar eventos.

Estados de consentimiento:

- `enabled`: eventos completos.
- `essential_only`: apertura, sesión y errores críticos.
- `disabled`: no envía eventos no esenciales.

El panel `/analytics-debug` solo se renderiza si `VITE_ENABLE_ANALYTICS_DEBUG=true`.

## Consultas SQL

Instalaciones/aperturas:

```sql
select count(distinct anonymous_id)
from analytics_events
where event_name = 'app_opened';
```

Retención D7:

```sql
select count(distinct anonymous_id)
from analytics_events
where event_name = 'retention_d7_returned';
```

Módulos más usados:

```sql
select properties->>'module_name' as module, count(*)
from analytics_events
where event_name = 'module_opened'
group by module
order by count desc;
```

Abandono:

```sql
select properties->>'funnel_name' as funnel,
       properties->>'last_step' as last_step,
       count(*)
from analytics_events
where event_name = 'funnel_abandoned'
group by funnel, last_step
order by count desc;
```

Razones de no pago:

```sql
select properties->>'reason' as reason, count(*)
from analytics_events
where event_name = 'paywall_dismissed'
group by reason
order by count desc;
```

## Eventos iniciales

- Apertura y sesión: `app_opened`, `session_started`, `session_ended`.
- Navegación/estado: `page_viewed`, `screen_viewed`.
- Módulos: dashboard, catálogo, mecánicos, clientes, servicios, nueva orden, OBD2 Scanner y Live Link.
- Embudos: `workshop_order_funnel`, `scanner_funnel`, `monetization_funnel`.
- Retención: D1, D3, D7, D14, D30 se emiten una sola vez por `anonymous_id`.

