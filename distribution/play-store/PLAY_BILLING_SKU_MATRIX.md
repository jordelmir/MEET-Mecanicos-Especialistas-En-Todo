# MEET — MATRIZ DE PRODUCTOS GOOGLE PLAY BILLING
## Guía de Alta de SKUs en Google Play Console

Para habilitar la monetización en la aplicación, estos identificadores deben darse de alta en **Google Play Console > Monetización > Productos integrados en la aplicación** y **Suscripciones**.

---

### 1. Suscripciones Recurrentes (Subscriptions)

| ID del Producto (Product ID) | Nombre en Play Console | Periodo de Facturación | Beneficio Asociado |
|---|---|---|---|
| `pro_monthly` | MEET Pro Mensual | 1 mes | Acceso completo a diagnóstico avanzado, live data ilimitado y exportación de reportes PDF |
| `pro_yearly` | MEET Pro Anual | 1 año | Acceso Pro con descuento anual |
| `elite_monthly` | MEET Elite Taller Mensual | 1 mes | Acceso multi-usuario de taller, funciones periciales avanzadas y terminal CAN-bus |
| `elite_yearly` | MEET Elite Taller Anual | 1 año | Acceso Elite con descuento anual |
| `fleet_starter_monthly` | MEET Flotas Starter | 1 mes | Gestión telemática de hasta 10 vehículos comerciales |
| `fleet_pro_monthly` | MEET Flotas Pro | 1 mes | Gestión telemática de flotas medianas y monitoreo de salud motor |

---

### 2. Productos Integrados de Compra Única (In-App Products)

#### A. Reportes y Peritaje (No Consumibles / Un solo uso por vehículo)
| ID del Producto | Nombre en Play Console | Tipo | Descripción |
|---|---|---|---|
| `report_pdf_single` | Reporte Certificado Individual | Consumible | Generación de 1 reporte de pre/post scan con hash SHA-256 y QR |
| `pre_purchase_report_single` | Peritaje Pre-Compra Vehicular | Consumible | Auditoría forense completa de compra-venta de auto usado |

#### B. Asistencia y Conexión en Vivo (LiveLink)
| ID del Producto | Nombre en Play Console | Tipo | Descripción |
|---|---|---|---|
| `livelink_30min` | Asistencia Mecánica en Vivo (30 min) | Consumible | Sesión de video/audio y telemetría en vivo con un especialista |
| `livelink_60min` | Asistencia Mecánica en Vivo (60 min) | Consumible | Sesión extendida de resolución técnica de averías complejas |

#### C. Paquetes de Herramientas y Desbloqueos de Instrumentos
| ID del Producto | Nombre en Play Console | Tipo | Descripción |
|---|---|---|---|
| `gauge_pack_premium` | Paquete de Indicadores Premium | No consumible | Desbloqueo de tablero de instrumentos digital avanzado |
| `oscilloscope_pack` | Módulo Osciloscopio Digital | No consumible | Análisis de forma de onda para sensores y actuadores |
| `manual_index_pack_local` | Índices de Manuales de Taller | No consumible | Diagramas de cableado y pares de apriete sin conexión |

#### D. Paquetes de Créditos
| ID del Producto | Nombre en Play Console | Tipo | Descripción |
|---|---|---|---|
| `ai_credit_pack_10` | 10 Consultas Diagnóstico IA | Consumible | Consultas guiadas al motor de inteligencia diagnóstica |
| `report_credit_pack_5` | Paquete de 5 Reportes Certificados | Consumible | Créditos para 5 reportes periciales |
| `livelink_credit_pack_3` | Paquete de 3 Sesiones LiveLink | Consumible | Créditos para 3 asistencias técnicas remotas |

#### E. Licencias Vitalicias (Lifetime Purchases)
| ID del Producto | Nombre en Play Console | Tipo | Descripción |
|---|---|---|---|
| `remove_ads_lifetime` | Sin Publicidad Permanente | No consumible | Experiencia limpia permanente sin anuncios |
| `premium_gauge_pack_lifetime` | Indicadores Premium Vitalicios | No consumible | Desbloqueo permanente de gauges |
| `offline_manual_tools_lifetime` | Herramientas Offline Vitalicias | No consumible | Descarga permanente de manuales técnicos |
