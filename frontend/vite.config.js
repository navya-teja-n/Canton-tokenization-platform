import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Base path is relative so the build works both at the root of a domain
// and under a GitHub Pages project path (https://<user>.github.io/<repo>/).
export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    port: 5173
  }
})
