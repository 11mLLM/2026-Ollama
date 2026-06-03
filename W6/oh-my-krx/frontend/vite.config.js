import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  optimizeDeps: {
    noDiscovery: true,
    include: [
      "@vitejs/plugin-react/preamble",
      "clsx",
      "lucide-react",
      "react",
      "react-dom",
      "react-dom/client",
      "react/jsx-dev-runtime",
      "react/jsx-runtime",
      "recharts",
      "three"
    ]
  },
  build: {
    outDir: "dist",
    chunkSizeWarningLimit: 900
  },
  server: {
    port: 5173,
    strictPort: true,
    host: true
  }
});
