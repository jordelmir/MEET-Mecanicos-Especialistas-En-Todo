export type AnalyticsConsentState = 'enabled' | 'essential_only' | 'disabled';

export type AnalyticsEventName =
  | 'app_opened'
  | 'session_started'
  | 'session_ended'
  | 'page_viewed'
  | 'screen_viewed'
  | 'module_opened'
  | 'module_exited'
  | 'funnel_step'
  | 'funnel_abandoned'
  | 'search_performed'
  | 'catalog_opened'
  | 'mechanics_opened'
  | 'clients_opened'
  | 'services_opened'
  | 'new_order_clicked'
  | 'order_created'
  | 'order_abandoned'
  | 'obd2_scanner_opened'
  | 'live_link_opened'
  | 'dashboard_viewed'
  | 'paywall_viewed'
  | 'paywall_cta_clicked'
  | 'paywall_dismissed'
  | 'purchase_started'
  | 'purchase_completed'
  | 'purchase_failed'
  | 'retention_d1_returned'
  | 'retention_d3_returned'
  | 'retention_d7_returned'
  | 'retention_d14_returned'
  | 'retention_d30_returned'
  | 'error_detected';

export type AnalyticsProperties = Record<string, unknown>;

export interface AnalyticsEvent {
  event_id: string;
  event_name: AnalyticsEventName;
  anonymous_id: string;
  user_id: string | null;
  session_id: string;
  timestamp: string;
  app_version: string;
  route: string;
  referrer: string;
  user_agent: string;
  device_type: string;
  viewport_width: number;
  viewport_height: number;
  locale: string;
  timezone: string;
  properties: AnalyticsProperties;
}

export interface AnalyticsQueueRecord {
  event: AnalyticsEvent;
  attempts: number;
  nextAttemptAt: number;
  lastError?: string;
}

export interface AnalyticsDebugSnapshot {
  anonymousId: string;
  sessionId: string;
  pendingEvents: number;
  recentEvents: AnalyticsEvent[];
  recentErrors: string[];
  lastFlushAt: string | null;
  consent: AnalyticsConsentState;
}

