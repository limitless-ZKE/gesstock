import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  // Tailwind ne sert qu'à la feuille de style : inutile (et coûteux) pendant les tests.
  plugins: [react(), ...(process.env.VITEST ? [] : [tailwindcss()])],
  server: {
    host: "localhost",
    port: 5173,
  },
  build: {
    rollupOptions: {
      output: {
        // Le socle (React, routeur, HTTP) change rarement : à part, il reste en cache d'une version à l'autre.
        manualChunks(id) {
          if (/node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler|axios)[\\/]/.test(id)) return "socle";
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    restoreMocks: true,
  },
});
