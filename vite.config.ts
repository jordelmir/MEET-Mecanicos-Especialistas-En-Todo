import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, '.', '');
    return {
      server: {
        port: 3000,
        host: '0.0.0.0',
      },
      plugins: [react()],
      define: {
        'process.env.API_KEY': JSON.stringify(env.GEMINI_API_KEY),
        'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY)
      },
      resolve: {
        alias: {
          '@': path.resolve(__dirname, '.'),
        }
      },
      build: {
        rollupOptions: {
          output: {
            manualChunks(id) {
              if (!id.includes('node_modules')) return undefined;
              if (id.includes('/node_modules/lucide-react/')) return 'vendor-icons';
              if (id.includes('/node_modules/@supabase/')) return 'vendor-supabase';
              if (id.includes('/node_modules/recharts/') || id.includes('/node_modules/d3-')) return 'vendor-charts';
              if (
                id.includes('/node_modules/react/')
                || id.includes('/node_modules/react-dom/')
                || id.includes('/node_modules/scheduler/')
              ) return 'vendor-react';
              return undefined;
            },
          },
        },
      }
    };
});
