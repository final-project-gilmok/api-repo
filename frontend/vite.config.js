import { defineConfig, loadEnv } from 'vite' // loadEnv 추가
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    // .env 파일의 환경 변수를 불러옵니다.
    const env = loadEnv(mode, process.cwd(), '')

    // ⭐️ 백엔드 프록시 타겟을 8081(API)에서 8080(Gateway)으로 변경하거나
    // 환경변수(VITE_API_BASE_URL)를 읽어오도록 합니다.
    const bypassHtml = (req) => {
        if (req.headers.accept?.includes('text/html')) return '/index.html'
    }

    // Gateway (8080) — 인증, 유저 등 Gateway를 거쳐야 하는 경로
    const gatewayProxy = {
        target: env.VITE_API_BASE_URL || 'http://localhost:8080',
        changeOrigin: true,
        bypass: bypassHtml,
    }

    // API 직접 (8081) — Gateway 라우팅에서 제외한 경로
    const apiDirectProxy = {
        target: 'http://localhost:8081',
        changeOrigin: true,
        bypass: bypassHtml,
    }

    return {
        plugins: [react()],
        server: {
            port: 3030,
            proxy: {
                // Gateway 경유
                '/admin': gatewayProxy,
                '/users': gatewayProxy,
                '/auth': gatewayProxy,
                // API 직접 연결 (Gateway에서 제외한 경로)
                '/api': apiDirectProxy,
                '/events': apiDirectProxy,
                '/queue': apiDirectProxy,
                '/reservations': apiDirectProxy,
            },
        },
    }
})
