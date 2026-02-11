import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/common/Layout'
import UserLayout from './components/common/UserLayout'
import EventManagement from './pages/EventManagement'
import EventDetail from './pages/EventDetail'
import PolicySettings from './pages/PolicySettings'
import Monitoring from './pages/Monitoring'
import AIRecommendation from './pages/AIRecommendation'
import AdminReservations from './pages/AdminReservations'
import EventList from './pages/user/EventList'
import UserEventDetail from './pages/user/UserEventDetail'
import QueueWaiting from './pages/user/QueueWaiting'
import SeatSelection from './pages/user/SeatSelection'
import ReservationConfirm from './pages/user/ReservationConfirm'
import ReservationResult from './pages/user/ReservationResult'
import MyReservations from './pages/user/MyReservations'

export default function App() {
  return (
    <Routes>
      {/* 사용자 페이지 */}
      <Route element={<UserLayout />}>
        <Route path="/" element={<EventList />} />
        <Route path="/events/:eventId" element={<UserEventDetail />} />
        <Route path="/events/:eventId/queue" element={<QueueWaiting />} />
        <Route path="/events/:eventId/seats" element={<SeatSelection />} />
        <Route path="/events/:eventId/reserve/confirm" element={<ReservationConfirm />} />
        <Route path="/reservations/:code" element={<ReservationResult />} />
        <Route path="/my-reservations" element={<MyReservations />} />
      </Route>

      {/* 어드민 페이지 */}
      <Route path="/admin" element={<Layout />}>
        <Route index element={<Navigate to="/admin/events" replace />} />
        <Route path="events" element={<EventManagement />} />
        <Route path="events/:eventId" element={<EventDetail />} />
        <Route path="events/:eventId/policy" element={<PolicySettings />} />
        <Route path="events/:eventId/monitoring" element={<Monitoring />} />
        <Route path="events/:eventId/reservations" element={<AdminReservations />} />
        <Route path="events/:eventId/ai-recommendation" element={<AIRecommendation />} />
        <Route path="ai-recommendation" element={<AIRecommendation />} />
        <Route path="settings" element={<div className="p-4">Settings (준비 중)</div>} />
        <Route path="profile" element={<div className="p-4">Admin Profile (준비 중)</div>} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
