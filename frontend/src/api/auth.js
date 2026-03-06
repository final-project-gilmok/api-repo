import { authApi } from './client'

export const authService = {
    signup: (signupData) => authApi.post('/auth/signup', signupData),
    login: (loginData) => authApi.post('/auth/login', loginData),
}
