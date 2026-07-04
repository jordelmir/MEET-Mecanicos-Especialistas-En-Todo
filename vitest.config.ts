/// <reference types="vitest" />
import { defineConfig } from 'vite';

/**
 * Vitest config is intentionally minimal. We only need it to understand
 * TypeScript paths for `lib/parts/**.ts` and to produce console output
 * that CI can read. Coverage is intentionally NOT configured here — add
 * @vitest/coverage-v8 (devDep) when the team is ready to enforce a bar.
 */
export default defineConfig({
  test: {
    include: ['lib/**/__tests__/**/*.test.ts'],
    environment: 'node',
    reporters: ['default'],
  },
});
