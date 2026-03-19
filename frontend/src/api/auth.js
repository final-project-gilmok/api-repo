import { authApi } from './client'

export const authService = {
    signup: (signupData) => authApi.post('/auth/signup', signupData, { skipAuthRedirect: true }),
    login: (loginData) => authApi.post('/auth/login', loginData, { skipAuthRedirect: true }),
    // skipAuthRedirect: 로그아웃 자체가 인증 관련 요청이므로 재발급 루프 방지
    logout: () => authApi.post('/auth/logout', undefined, { skipAuthRedirect: true }),
}
