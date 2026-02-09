import { useParams, NavLink } from 'react-router-dom'

const base = (eventId) => `/admin/events/${eventId}`
const tabs = [
  { to: 'policy', label: '정책 설정' },
  { to: 'monitoring', label: '모니터링' },
  { to: 'ai-recommendation', label: 'AI 추천' },
]

const MOCK_METRICS = [
  { label: '현재 RPS (Requests Per Second)', value: '125 요청/초', change: '+15%', updated: '10:29:58', desc: '초당 처리되는 요청 수', warn: false },
  { label: '4xx 오류율 (Client Error Rate)', value: '0.8%', change: '+0.2%', updated: '10:29:58', desc: '클라이언트 요청 오류율', warn: true },
  { label: '5xx 오류율 (Server Error Rate)', value: '0.1%', change: '-0.1%', updated: '10:29:58', desc: '서버 내부 오류율', warn: false },
  { label: 'p95 지연 시간 (95th Percentile Latency)', value: '120 ms', change: '+20ms', updated: '10:29:57', desc: '95번째 백분위수 요청 지연 시간', warn: true },
  { label: 'p99 지연 시간 (99th Percentile Latency)', value: '350 ms', change: '+50ms', updated: '10:29:57', desc: '99번째 백분위수 요청 지연 시간', warn: false },
  { label: '현재 대기열 길이 (Current Queue Length)', value: '50명', change: '+10', updated: '10:29:59', desc: '현재 대기열에 있는 사용자 수', warn: false },
]

/**
 * Render the event monitoring page scoped to the current event.
 *
 * Renders a header showing the event ID, a tabbed navigation for event sections, and a responsive grid of metric cards (label, change indicator, value, description, and last-updated time).
 * @returns {JSX.Element} The monitoring page React element for the event identified by the current route.
 */
export default function Monitoring() {
  const { eventId } = useParams()
  const path = base(eventId)

  return (
    <>
      <h1 className="h3 mb-1 fw-bold">이벤트 모니터링: {eventId}</h1>
      <p className="text-muted mb-2">전체 대시보드 업데이트: 2024년 7월 20일 오전 10:30</p>

      <ul className="nav event-detail-tabs nav-tabs mb-4">
        {tabs.map((tab) => (
          <li key={tab.to} className="nav-item">
            <NavLink className="nav-link" to={`${path}/${tab.to}`}>
              {tab.label}
            </NavLink>
          </li>
        ))}
      </ul>

      <div className="row g-3">
        {MOCK_METRICS.map((m) => (
          <div key={m.label} className="col-md-6 col-lg-4">
            <div className="card metric-card h-100">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <h3 className="h6 fw-semibold mb-0">
                    {m.warn && (
                      <span className="text-danger me-1" title="주의">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>
                      </span>
                    )}
                    {m.label}
                  </h3>
                  <span className={`small ${m.change.startsWith('+') ? 'text-danger' : 'text-success'}`}>{m.change}</span>
                </div>
                <p className="fs-5 fw-bold text-primary mb-1">{m.value}</p>
                <p className="text-muted small mb-0">{m.desc}</p>
                <p className="text-muted small mb-0 mt-1">최근 업데이트: {m.updated}</p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </>
  )
}