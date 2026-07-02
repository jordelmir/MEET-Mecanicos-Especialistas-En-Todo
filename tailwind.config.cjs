/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './index.html',
    './App.tsx',
    './components/**/*.{ts,tsx}',
    './lib/**/*.{ts,tsx}',
    './services/**/*.{ts,tsx}',
    './src/**/*.{ts,tsx}',
    './types.ts',
    './constants.ts',
  ],
  safelist: [
    'glass',
    'industrial-grid',
    'text-forge-500',
    'bg-meet-panel',
  ],
  theme: {
    extend: {
      colors: {
        forge: {
          500: '#22d3ee',
          600: '#0891b2',
        },
        meet: {
          panel: '#0f172a',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['Orbitron', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['IBM Plex Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
    },
  },
  plugins: [],
};
