import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const apiBase = env.VITE_API_BASE_URL || 'http://localhost:8081'
    const authBase = env.VITE_AUTH_BASE_URL || 'http://localhost:9000'

    const backendProxy = {
        target: apiBase,
        changeOrigin: true,
        bypass(req) {
            if (req.headers.accept?.includes('text/html')) {
                return '/index.html'
            }
        },
    }

    const authProxy = {
        target: authBase,
        changeOrigin: true,
        bypass(req) {
            if (req.headers.accept?.includes('text/html')) {
                return '/index.html'
            }
        },
    }

    return {
        plugins: [react()],
        server: {
            port: 3030,
            proxy: {
                '/auth': authProxy,
                '/users': backendProxy,
                '/events': backendProxy,
                '/admin': backendProxy,
                '/reservations': backendProxy,
                '/queue': backendProxy,
            },
        },
    }
})