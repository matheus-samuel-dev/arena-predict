import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

const brandValues = {
  name: process.env.VITE_APP_NAME || "ArenaPredict",
  shortName: process.env.VITE_APP_SHORT_NAME || "Arena",
  tagline: process.env.VITE_APP_TAGLINE || "Sua leitura. Sua arena.",
  description: process.env.VITE_APP_DESCRIPTION || "Previsões esportivas, bolões e ligas com pontos exclusivamente virtuais.",
};

function brandAssets(): Plugin {
  const manifest = JSON.stringify({
    name: brandValues.name,
    short_name: brandValues.shortName,
    description: brandValues.description,
    lang: "pt-BR",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "any",
    background_color: "#080b14",
    theme_color: "#080b14",
    categories: ["sports", "entertainment", "social"],
    icons: [
      { src: "/android-chrome-192x192.png", sizes: "192x192", type: "image/png", purpose: "any maskable" },
      { src: "/android-chrome-512x512.png", sizes: "512x512", type: "image/png", purpose: "any maskable" },
    ],
  }, null, 2);
  const renderHtml = (html: string) => html
    .replaceAll("__APP_NAME__", brandValues.name)
    .replaceAll("__APP_SHORT_NAME__", brandValues.shortName)
    .replaceAll("__APP_TAGLINE__", brandValues.tagline)
    .replaceAll("__APP_DESCRIPTION__", brandValues.description);
  return {
    name: "arena-brand-assets",
    transformIndexHtml: renderHtml,
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        if (request.url?.split("?")[0] !== "/manifest.webmanifest") return next();
        response.statusCode = 200;
        response.setHeader("Content-Type", "application/manifest+json; charset=utf-8");
        response.end(manifest);
      });
    },
    writeBundle(options) {
      writeFileSync(resolve(options.dir || "dist", "manifest.webmanifest"), `${manifest}\n`, "utf8");
    },
  };
}

export default defineConfig({
  plugins: [react(), brandAssets()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_BACKEND_PROXY || "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  preview: { host: "0.0.0.0", port: 5173 },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    css: true,
  },
});
