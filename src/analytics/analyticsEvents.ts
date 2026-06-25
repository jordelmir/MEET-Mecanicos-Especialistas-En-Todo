import type { AnalyticsEventName } from './analyticsTypes';

export const ANALYTICS_EVENTS: Record<Uppercase<AnalyticsEventName>, AnalyticsEventName> = {
  APP_OPENED: 'app_opened',
  SESSION_STARTED: 'session_started',
  SESSION_ENDED: 'session_ended',
  PAGE_VIEWED: 'page_viewed',
  SCREEN_VIEWED: 'screen_viewed',
  MODULE_OPENED: 'module_opened',
  MODULE_EXITED: 'module_exited',
  FUNNEL_STEP: 'funnel_step',
  FUNNEL_ABANDONED: 'funnel_abandoned',
  SEARCH_PERFORMED: 'search_performed',
  CATALOG_OPENED: 'catalog_opened',
  MECHANICS_OPENED: 'mechanics_opened',
  CLIENTS_OPENED: 'clients_opened',
  SERVICES_OPENED: 'services_opened',
  NEW_ORDER_CLICKED: 'new_order_clicked',
  ORDER_CREATED: 'order_created',
  ORDER_ABANDONED: 'order_abandoned',
  OBD2_SCANNER_OPENED: 'obd2_scanner_opened',
  LIVE_LINK_OPENED: 'live_link_opened',
  DASHBOARD_VIEWED: 'dashboard_viewed',
  PAYWALL_VIEWED: 'paywall_viewed',
  PAYWALL_CTA_CLICKED: 'paywall_cta_clicked',
  PAYWALL_DISMISSED: 'paywall_dismissed',
  PURCHASE_STARTED: 'purchase_started',
  PURCHASE_COMPLETED: 'purchase_completed',
  PURCHASE_FAILED: 'purchase_failed',
  RETENTION_D1_RETURNED: 'retention_d1_returned',
  RETENTION_D3_RETURNED: 'retention_d3_returned',
  RETENTION_D7_RETURNED: 'retention_d7_returned',
  RETENTION_D14_RETURNED: 'retention_d14_returned',
  RETENTION_D30_RETURNED: 'retention_d30_returned',
  ERROR_DETECTED: 'error_detected',
};

export const ESSENTIAL_ANALYTICS_EVENTS = new Set<AnalyticsEventName>([
  'app_opened',
  'session_started',
  'session_ended',
  'error_detected',
]);

