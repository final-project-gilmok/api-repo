import { api } from './client'

export function getPolicy(eventId) {
  return api.get(`/admin/events/${eventId}/policy`)
}

export function updatePolicy(eventId, body) {
  return api.put(`/admin/events/${eventId}/policy`, body)
}

export function getMetrics(eventId) {
  return api.get(`/admin/events/${eventId}/metrics`)
}

export function getRecommendation(eventId) {
  return api.get(`/admin/events/${eventId}/ai`)
}
