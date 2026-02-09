import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/common/Layout'
import EventManagement from './pages/EventManagement'
import EventDetail from './pages/EventDetail'
import PolicySettings from './pages/PolicySettings'
import Monitoring from './pages/Monitoring'
import AIRecommendation from './pages/AIRecommendation'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/admin/events" replace />} />
      <Route path="/admin" element={<Layout />}>
        <Route index element={<Navigate to="/admin/events" replace />} />
        <Route path="events" element={<EventManagement />} />
        <Route path="events/:eventId" element={<EventDetail />} />
        <Route path="events/:eventId/policy" element={<PolicySettings />} />
        <Route path="events/:eventId/monitoring" element={<Monitoring />} />
        <Route path="events/:eventId/ai-recommendation" element={<AIRecommendation />} />
        <Route path="ai-recommendation" element={<AIRecommendation />} />
        <Route path="settings" element={<div className="p-4">Settings (준비 중)</div>} />
        <Route path="profile" element={<div className="p-4">Admin Profile (준비 중)</div>} />
      </Route>
      <Route path="*" element={<Navigate to="/admin/events" replace />} />
    </Routes>
  )
}
