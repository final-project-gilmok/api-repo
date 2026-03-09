import { api } from './client'

export function getPolicy(eventId) {
  return api.get(`/admin/events/${eventId}/policy`)
}

export function updatePolicy(eventId, body) {
  return api.put(`/admin/events/${eventId}/policy`, body)
}
