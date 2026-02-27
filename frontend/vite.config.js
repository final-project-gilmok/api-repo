import { defineConfig, loadEnv } from 'vite' // loadEnv 추가
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
    // .env 파일의 환경 변수를 불러옵니다.
    const env = loadEnv(mode, process.cwd(), '')

    // ⭐️ 백엔드 프록시 타겟을 8081(API)에서 8080(Gateway)으로 변경하거나
    // 환경변수(VITE_API_BASE_URL)를 읽어오도록 합니다.
    const backendProxy = {
        target: env.VITE_API_BASE_URL || 'http://localhost:8080', // ⭐️ 8081 -> 8080 으로 변경!
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
            port: 3030, // 포트는 그대로 두셔도 됩니다 (Gateway에 이미 허용해둠)
            proxy: {
                // 프론트엔드에서 /api/events 등으로 쏘면 자동으로 http://localhost:8080/api/events 로 넘어갑니다
                '/api': backendProxy,
                '/events': backendProxy,
                '/admin': backendProxy,
                '/reservations': backendProxy,
                '/queue': backendProxy,
            },
        },
    }
})