import type { AnalyticsConsentState } from './analyticsTypes';

const CONSENT_KEY = 'meet_analytics_consent';
const DEFAULT_CONSENT: AnalyticsConsentState = 'enabled';

function isConsentState(value: string | null): value is AnalyticsConsentState {
  return value === 'enabled' || value === 'essential_only' || value === 'disabled';
}

export const AnalyticsConsentManager = {
  getConsent(): AnalyticsConsentState {
    if (typeof window === 'undefined') return DEFAULT_CONSENT;
    const stored = window.localStorage.getItem(CONSENT_KEY);
    return isConsentState(stored) ? stored : DEFAULT_CONSENT;
  },

  setConsent(consent: AnalyticsConsentState) {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(CONSENT_KEY, consent);
    window.dispatchEvent(new CustomEvent('meet:analytics-consent-changed', { detail: consent }));
  },

  subscribe(listener: (consent: AnalyticsConsentState) => void): () => void {
    if (typeof window === 'undefined') return () => undefined;
    const handler = (event: Event) => {
      listener((event as CustomEvent<AnalyticsConsentState>).detail ?? this.getConsent());
    };
    window.addEventListener('meet:analytics-consent-changed', handler);
    return () => window.removeEventListener('meet:analytics-consent-changed', handler);
  },
};

