import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '')
    const gatewayBase = env.VITE_GATEWAY_URL || env.VITE_API_BASE_URL || 'http://localhost:8080'

    const bypassHtml = (req) => {
        if (req.headers.accept?.includes('text/html')) {
            return '/index.html'
        }
    }

    const gatewayProxy = {
        target: gatewayBase,
        changeOrigin: true,
        bypass: bypassHtml,
    }

    return {
        plugins: [react()],
        server: {
            port: 3030,
            proxy: {
                '/auth': gatewayProxy,
                '/users': gatewayProxy,
                '/events': gatewayProxy,
                '/admin': gatewayProxy,
                '/reservations': gatewayProxy,
                '/queue': gatewayProxy,
            },
        },
    }
})