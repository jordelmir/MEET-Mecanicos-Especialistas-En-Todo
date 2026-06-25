import { supabase } from '../../lib/supabase';
import { ANALYTICS_EVENTS, ESSENTIAL_ANALYTICS_EVENTS } from './analyticsEvents';
import { AnalyticsConsentManager } from './analyticsConsent';
import { analyticsQueue } from './analyticsQueue';
import { getAnonymousId, getOpenStats, getSessionId, markAppOpen } from './analyticsSession';
import type { AnalyticsEvent, AnalyticsEventName, AnalyticsProperties } from './analyticsTypes';

const APP_VERSION = import.meta.env.VITE_APP_VERSION ?? '2.2.0-web';
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SENSITIVE_KEYS = ['email', 'phone', 'telefono', 'vin', 'address', 'direccion', 'identification', 'cedula', 'accesscode', 'token', 'purchasetoken'];

function eventId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function deviceType(): string {
  if (typeof navigator === 'undefined') return 'unknown';
  const ua = navigator.userAgent.toLowerCase();
  if (/ipad|tablet/.test(ua)) return 'tablet';
  if (/mobi|android|iphone/.test(ua)) return 'mobile';
  return 'desktop';
}

function normalizeUserId(userId?: string | null): string | null {
  if (!userId) return null;
  return UUID_RE.test(userId) ? userId : null;
}

function sanitizeProperties(input: AnalyticsProperties = {}): AnalyticsProperties {
  const sanitizeValue = (value: unknown): unknown => {
    if (Array.isArray(value)) return value.map(sanitizeValue).slice(0, 50);
    if (value && typeof value === 'object') {
      const clean: AnalyticsProperties = {};
      Object.entries(value as Record<string, unknown>).forEach(([key, nestedValue]) => {
        const lowered = key.toLowerCase();
        if (SENSITIVE_KEYS.some(secret => lowered.includes(secret))) return;
        clean[key] = sanitizeValue(nestedValue);
      });
      return clean;
    }
    if (typeof value === 'string') return value.slice(0, 500);
    return value;
  };
  return sanitizeValue(input) as AnalyticsProperties;
}

function shouldTrack(eventName: AnalyticsEventName): boolean {
  const consent = AnalyticsConsentManager.getConsent();
  if (consent === 'disabled') return false;
  if (consent === 'essential_only') return ESSENTIAL_ANALYTICS_EVENTS.has(eventName);
  return true;
}

function createEvent(eventName: AnalyticsEventName, properties: AnalyticsProperties = {}, userId?: string | null): AnalyticsEvent {
  const viewport = typeof window !== 'undefined'
    ? { width: window.innerWidth, height: window.innerHeight }
    : { width: 0, height: 0 };
  return {
    event_id: eventId(),
    event_name: eventName,
    anonymous_id: getAnonymousId(),
    user_id: normalizeUserId(userId ?? null),
    session_id: getSessionId(),
    timestamp: new Date().toISOString(),
    app_version: APP_VERSION,
    route: typeof window !== 'undefined' ? `${window.location.pathname}${window.location.search}` : '',
    referrer: typeof document !== 'undefined' ? document.referrer : '',
    user_agent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
    device_type: deviceType(),
    viewport_width: viewport.width,
    viewport_height: viewport.height,
    locale: typeof navigator !== 'undefined' ? navigator.language : 'unknown',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    properties: sanitizeProperties({
      ...properties,
      open_stats: getOpenStats(),
    }),
  };
}

async function uploadEvents(events: AnalyticsEvent[]) {
  if (!import.meta.env.VITE_SUPABASE_URL || !import.meta.env.VITE_SUPABASE_ANON_KEY) {
    throw new Error('Supabase analytics credentials are not configured');
  }

  const payload = events.map(event => ({
    id: event.event_id,
    event_name: event.event_name,
    anonymous_id: event.anonymous_id,
    user_id: event.user_id,
    session_id: event.session_id,
    event_timestamp: event.timestamp,
    app_version: event.app_version,
    route: event.route,
    referrer: event.referrer,
    user_agent: event.user_agent,
    device_type: event.device_type,
    viewport_width: event.viewport_width,
    viewport_height: event.viewport_height,
    locale: event.locale,
    timezone: event.timezone,
    properties: event.properties,
  }));

  const { error } = await supabase.from('analytics_events').insert(payload);
  if (error) throw error;
}

export const analytics = {
  track(eventName: AnalyticsEventName, properties?: AnalyticsProperties, userId?: string | null) {
    if (!shouldTrack(eventName)) return;
    void analyticsQueue.enqueue(createEvent(eventName, properties, userId));
  },

  screenViewed(screenName: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.SCREEN_VIEWED, { screen_name: screenName, ...properties });
  },

  moduleOpened(moduleName: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.MODULE_OPENED, { module_name: moduleName, ...properties });
  },

  moduleExited(moduleName: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.MODULE_EXITED, { module_name: moduleName, ...properties });
  },

  funnelStep(funnelName: string, stepName: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.FUNNEL_STEP, { funnel_name: funnelName, step_name: stepName, ...properties });
  },

  funnelAbandoned(funnelName: string, lastStep: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.FUNNEL_ABANDONED, { funnel_name: funnelName, last_step: lastStep, ...properties });
  },

  paywallViewed(source: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.PAYWALL_VIEWED, { source, ...properties });
  },

  purchaseStarted(productId: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.PURCHASE_STARTED, { product_id: productId, ...properties });
  },

  purchaseCompleted(productId: string, amount: number, currency: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.PURCHASE_COMPLETED, { product_id: productId, amount, currency, ...properties });
  },

  purchaseFailed(productId: string, reason: string, properties?: AnalyticsProperties) {
    this.track(ANALYTICS_EVENTS.PURCHASE_FAILED, { product_id: productId, reason, ...properties });
  },

  startSession(userId?: string | null) {
    this.track(ANALYTICS_EVENTS.APP_OPENED, undefined, userId);
    this.track(ANALYTICS_EVENTS.SESSION_STARTED, undefined, userId);
    markAppOpen().forEach(signal => this.track(signal.eventName, signal, userId));
  },

  endSession() {
    this.track(ANALYTICS_EVENTS.SESSION_ENDED);
  },

  flush() {
    return analyticsQueue.flush(uploadEvents);
  },

  retryFailed() {
    return analyticsQueue.retryFailed(uploadEvents);
  },

  debugSnapshot() {
    return analyticsQueue.snapshot();
  },
};

