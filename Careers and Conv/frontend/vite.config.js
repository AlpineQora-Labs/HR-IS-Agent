import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server proxies the runtime API to Olivia's backend on :8092, so the SPA calls
// same-origin /v1/* paths in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/v1': {
        target: 'http://localhost:8092',
        changeOrigin: true,
      },
    },
  },
});
