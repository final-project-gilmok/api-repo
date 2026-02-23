import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backendProxy = {
  target: 'http://localhost:8081',
  changeOrigin: true,
  bypass(req) {
    // 브라우저 페이지 요청(F5 등)은 SPA로, API 호출만 백엔드로 프록시
    if (req.headers.accept?.includes('text/html')) {
      return '/index.html'
    }
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3030,
    proxy: {
      '/events': backendProxy,
      '/admin': backendProxy,
      '/reservations': backendProxy,
      '/queue': backendProxy,
    },
  },
})
