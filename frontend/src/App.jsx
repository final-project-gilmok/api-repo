import { Routes, Route, Navigate } from 'react-router-dom'
import Login, { AdminRouteGuard } from './pages/auth/Login'
import UserLayout from './components/common/UserLayout'
import EventManagement from './pages/admin/EventManagement.jsx'
import EventDetail from './pages/admin/EventDetail.jsx'
import EventDetailLayout from './components/admin/EventDetailLayout.jsx'
import PolicySettings from './pages/admin/PolicySettings.jsx'
import Monitoring from './pages/admin/Monitoring.jsx'
import AIRecommendation from './pages/admin/AIRecommendation.jsx'
import AdminReservations from './pages/admin/AdminReservations.jsx'
import AdminSeatManagement from './pages/admin/AdminSeatManagement.jsx'
import EventList from './pages/user/EventList'
import UserEventDetail from './pages/user/UserEventDetail'
import QueueWaiting from './pages/user/QueueWaiting'
import SeatSelection from './pages/user/SeatSelection'
import ReservationConfirm from './pages/user/ReservationConfirm'
import ReservationResult from './pages/user/ReservationResult'
import MyReservations from './pages/user/MyReservations'
import MyPage from './pages/user/MyPage'
import Signup from './pages/auth/Signup'



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
        <Route path="/my" element={<MyPage />} />
        <Route path="/my-reservations" element={<MyReservations />} />
        <Route path="/auth/signup" element={<Signup />} />
        <Route path="/auth/login" element={<Login />} />
      </Route>



      {/* 어드민 페이지 (로그인 + ADMIN role 필요) */}
      <Route path="/admin" element={<AdminRouteGuard />}>
        <Route index element={<Navigate to="/admin/events" replace />} />
        <Route path="events" element={<EventManagement />} />
        <Route path="events/:eventId" element={<EventDetailLayout />}>
          <Route index element={<EventDetail />} />
          <Route path="policy" element={<PolicySettings />} />
          <Route path="seats" element={<AdminSeatManagement />} />
          <Route path="reservations" element={<AdminReservations />} />
          <Route path="monitoring" element={<Monitoring />} />
          <Route path="ai-recommendation" element={<AIRecommendation />} />
        </Route>
        <Route path="ai-recommendation" element={<AIRecommendation />} />
        <Route path="settings" element={<div className="p-4">Settings (준비 중)</div>} />
        <Route path="profile" element={<div className="p-4">Admin Profile (준비 중)</div>} />
        <Route path="logs" element={<Monitoring />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
