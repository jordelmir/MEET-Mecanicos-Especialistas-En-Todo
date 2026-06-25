export const ANALYTICS_FUNNELS = {
  workshopOrder: {
    name: 'workshop_order_funnel',
    steps: ['dashboard_viewed', 'new_order_clicked', 'client_selected', 'service_selected', 'mechanic_selected', 'order_created'],
  },
  scanner: {
    name: 'scanner_funnel',
    steps: ['obd2_scanner_opened', 'connection_started', 'connection_success', 'scan_started', 'scan_completed', 'results_viewed'],
  },
  monetization: {
    name: 'monetization_funnel',
    steps: ['paywall_viewed', 'paywall_cta_clicked', 'purchase_started', 'purchase_completed'],
  },
} as const;

