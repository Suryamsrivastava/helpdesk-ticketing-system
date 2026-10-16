import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// /api is proxied to the Spring Boot backend, so the browser never needs CORS in dev
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
