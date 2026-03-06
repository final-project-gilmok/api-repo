import { authApi } from './client'

export const authService = {
    signup: (signupData) => authApi.post('/auth/signup', signupData, { skipAuthRedirect: true }),
    login: (loginData) => authApi.post('/auth/login', loginData, { skipAuthRedirect: true }),
}
