import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  define: {
    global: "globalThis",
  },
  optimizeDeps: {
    include: ["mqtt"],
  },
  test: {
    // The console's logic lives in plain js modules under src/lib, so the node
    // environment is enough and keeps the suite fast.
    environment: "node",
    include: ["src/**/*.test.{js,jsx}"],
  },
  build: {
    rollupOptions: {
      output: {
        // Split the vendors that dominate the bundle into independent chunks.
        // They rarely change together, so browsers keep them cached across
        // deploys, and the heavy ones (leaflet, firebase) stop blocking the
        // first paint of the shell.
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          if (id.includes("react-leaflet") || id.includes("leaflet")) return "leaflet";
          if (id.includes("firebase")) return "firebase";
          if (id.includes("mqtt")) return "mqtt";
          if (id.includes("lucide-react")) return "icons";
          if (id.includes("react-dom") || id.includes("react") || id.includes("scheduler")) return "react";
          return undefined;
        },
      },
    },
  },
});
