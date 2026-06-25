import { useEffect, useRef } from 'react';
import { analytics } from './analyticsClient';
import { ANALYTICS_EVENTS } from './analyticsEvents';

export function useAnalyticsLifecycle(userId?: string | null) {
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    analytics.startSession(userId);

    const flush = () => {
      void analytics.flush();
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') flush();
    };
    const interval = window.setInterval(flush, 30_000);
    window.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('beforeunload', flush);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('beforeunload', flush);
      analytics.endSession();
      flush();
    };
  }, [userId]);
}

export function useAnalyticsScreen(screenName: string, properties?: Record<string, unknown>) {
  const lastScreenRef = useRef<string>('');

  useEffect(() => {
    if (!screenName || lastScreenRef.current === screenName) return;
    lastScreenRef.current = screenName;
    analytics.track(ANALYTICS_EVENTS.PAGE_VIEWED, { page_name: screenName, ...properties });
    analytics.screenViewed(screenName, properties);
  }, [screenName, properties]);
}

