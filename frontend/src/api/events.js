import { api } from './client'

export function getEvents() {
  return api.get('/admin/events')
}

export function getEvent(eventId) {
  return api.get(`/admin/events/${eventId}`)
}

export function createEvent(body) {
  return api.post('/admin/events', body)
}

export function openEvent(eventId) {
  return api.post(`/admin/events/${eventId}/open`, {})
}

export function closeEvent(eventId) {
  return api.post(`/admin/events/${eventId}/close`, {})
}
