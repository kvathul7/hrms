import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig({
  plugins: [react()],
  // Electron loads index.html over file://, where the default base of '/' makes
  // /assets/... resolve to the drive root (C:\assets\...) and the bundle 404s to
  // a blank white window. './' keeps asset URLs relative to index.html.
  base: './',
  server: { port: 17824, proxy: { '/api': { target: 'http://localhost:17823', changeOrigin: true } } },
  build: { outDir: '../electron/frontend-dist', emptyOutDir: true }
})
