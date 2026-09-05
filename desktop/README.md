# MEET Desktop

Electron wrapper for the MEET web dashboard.

## Setup

```bash
npm install
npm run dev          # Vite dev server + Electron
npm run build        # Production build
```

## Architecture

- Vite serves the web app (same as `/web`)
- Electron wraps it in a native window
- Shares API client with web (`/web/src/api/client.ts`)
- Uses system tray for quick access
- Offline-capable with service worker

## Features

- Native menu bar
- System tray integration
- Auto-updater (future)
- Push notifications (future)
