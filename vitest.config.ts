/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';

/**
 * Vitest config is intentionally minimal. We only need it to understand
 * TypeScript paths for `lib/**.ts` and to produce console output that CI
 * can read. Coverage is intentionally NOT configured here.
 */
export default defineConfig({
  test: {
    include: ['lib/**/__tests__/**/*.test.ts'],
    environment: 'node',
    reporters: ['default'],
  },
});
