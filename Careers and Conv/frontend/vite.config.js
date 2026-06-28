import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server proxies the runtime API to Olivia's backend on :8092, so the SPA calls
// same-origin /v1/* paths in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    // The designed careers homepage (bofa-careers-home.html) is served on :5173 as the
    // front door; this React screening assistant runs on :5174 and the homepage opens it.
    port: 5174,
    proxy: {
      '/v1': {
        target: 'http://localhost:8092',
        changeOrigin: true,
      },
    },
  },
});
