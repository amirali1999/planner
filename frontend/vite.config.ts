import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Dev server on loopback only; /api is proxied to the local backend, so the browser stays same-origin.
// PLANNER_API_TARGET overrides the backend URL (e.g. when another instance already uses port 8080).
const apiTarget = process.env.PLANNER_API_TARGET ?? 'http://127.0.0.1:8080';

const proxy = { '/api': { target: apiTarget, changeOrigin: false } };

export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy },
  // `npm run preview` serves the production bundle; it needs the same /api proxy.
  preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
  test: {
    environment: 'node',
    setupFiles: ['src/test-setup.ts'],
  },
});
