import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev proxy so the app can call `/api/...` without CORS hassles.
// Backend local default is :8080 (per repo README).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});

