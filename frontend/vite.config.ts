/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';

const backend = process.env.PTC_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
  plugins: [
    react(),
    VitePWA({
      // Custom service worker (src/sw.ts): precache app shell + runtime
      // caching rules + push handling. registerType 'prompt' so a new
      // version is announced and applied only on the user's confirmation.
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'prompt',
      injectRegister: false,
      injectManifest: { globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'], globIgnores: ['guide/**'], maximumFileSizeToCacheInBytes: 3 * 1024 * 1024 },
      devOptions: { enabled: false },
      manifest: {
        name: 'PTC Tracker',
        short_name: 'PTC',
        description: 'Progress tracker for Public Tender Committees',
        lang: 'en',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'any',
        theme_color: '#0b4f6c',
        background_color: '#f6f8fa',
        icons: [
          { src: '/icons/icon-64.png', sizes: '64x64', type: 'image/png' },
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          query: ['@tanstack/react-query'],
          radix: ['@radix-ui/react-dialog', '@radix-ui/react-select', '@radix-ui/react-tabs', '@radix-ui/react-toast', '@radix-ui/react-checkbox'],
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/.well-known': { target: backend, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts', 'test/**/*.test.tsx'],
    setupFiles: ['test/setup.ts'],
  },
});
