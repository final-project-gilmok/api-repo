import { api } from './client'

export function getMe() {
  return api.get('/users/me')
}

export function getDashboard() {
  return api.get('/users/me/dashboard')
}

export function getMyEvents() {
  return api.get('/users/me/events')
}

