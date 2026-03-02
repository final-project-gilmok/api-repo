import { Outlet, useParams, NavLink } from 'react-router-dom'

const eventTabs = [
  { to: 'policy', label: '정책 설정' },
  { to: 'seats', label: '좌석 관리' },
  { to: 'reservations', label: '예약 현황' },
  { to: 'monitoring', label: '모니터링' },
  { to: 'ai-recommendation', label: 'AI 추천' },
]

export default function EventDetailLayout() {
  const { eventId } = useParams()

  return (
    <>
      <nav className="nav nav-tabs mb-3 flex-nowrap overflow-auto">
        {eventTabs.map(({ to, label }) => (
          <NavLink
            key={to}
            to={`/admin/events/${eventId}/${to}`}
            className={({ isActive }) =>
              `nav-link ${isActive ? 'active' : ''} text-nowrap`
            }
          >
            {label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  )
}
