import { useParams, Link } from 'react-router-dom'

const MOCK_EVENT_BY_ID = {
  evt_4a7b8c9d: {
    name: '2024 데모 이벤트',
    description: '차세대 데모 예약 시스템의 주요 기능을 시연합니다. 트래픽 제어 정책 및 AI 추천을 통해 최적의 운영 방안을 제시합니다.',
    createdAt: '2024-03-15',
    status: 'OPEN',
  },
  EVT001: { name: '2024년 기술 컨퍼런스', description: '기술 컨퍼런스 이벤트입니다.', createdAt: '2024-01-15', status: 'OPEN' },
  EVT002: { name: '새로운 제품 런칭 데모', description: '제품 런칭 데모.', createdAt: '2024-02-20', status: 'DRAFT' },
  EVT003: { name: '클라우드 보안 웨비나', description: '보안 웨비나.', createdAt: '2023-11-10', status: 'CLOSED' },
  EVT004: { name: 'AI 혁신 포럼', description: 'AI 포럼.', createdAt: '2024-03-01', status: 'OPEN' },
  EVT005: { name: '데이터 분석 입문 과정', description: '데이터 분석 과정.', createdAt: '2024-04-05', status: 'DRAFT' },
}
const defaultEvent = {
  name: '데모 이벤트',
  description: '이벤트 설명을 입력하세요.',
  createdAt: new Date().toISOString().slice(0, 10),
  status: 'DRAFT',
}

const quickLinks = [
  {
    to: 'policy',
    title: '정책 설정',
    desc: '이벤트의 트래픽 제어 및 대기열 정책을 설정합니다.',
    icon: 'gear',
  },
  {
    to: 'monitoring',
    title: '모니터링',
    desc: '이벤트의 실시간 RPS, 에러율, 대기열 길이 등을 모니터링합니다.',
    icon: 'monitor',
  },
  {
    to: 'ai-recommendation',
    title: 'AI 추천',
    desc: 'AI가 최적의 이벤트 운영 정책을 추천하고 적용합니다.',
    icon: 'bulb',
  },
]

export default function EventDetail() {
  const { eventId } = useParams()
  const event = { id: eventId, ...(MOCK_EVENT_BY_ID[eventId] || defaultEvent) }

  return (
    <>
      <h1 className="h3 mb-1 fw-bold">이벤트 상세</h1>
      <p className="text-muted mb-4">{event.name}</p>

      <div className="card border rounded-3 mb-4">
        <div className="card-body">
          <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
            <div>
              <h2 className="h4 mb-2">{event.name}</h2>
              <p className="text-muted small mb-1">이벤트 ID: {event.id}</p>
              <p className="mb-2">{event.description}</p>
              <p className="text-muted small mb-0">생성일: {event.createdAt}</p>
            </div>
            <div className="d-flex align-items-center gap-2">
              <span className="badge bg-light text-dark border">{event.status}</span>
              <button type="button" className="btn btn-primary btn-sm">이벤트 오픈</button>
              <button type="button" className="btn btn-outline-secondary btn-sm">이벤트 종료</button>
            </div>
          </div>
        </div>
      </div>

      <h2 className="h5 fw-semibold mb-3">빠른 관리 링크</h2>
      <div className="row g-3">
        {quickLinks.map((link) => (
          <div key={link.to} className="col-md-4">
            <Link
              to={`/admin/events/${eventId}/${link.to}`}
              className="card quick-link-card p-3 text-decoration-none text-body"
            >
              <div className="d-flex align-items-start gap-2">
                <span className="text-primary">
                  {link.icon === 'gear' && (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M19.14 12.94c.04-.31.06-.63.06-.94 0-.31-.02-.63-.06-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg>
                  )}
                  {link.icon === 'monitor' && (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M20 18c1.1 0 1.99-.9 1.99-2L22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2H0v2h24v-2h-4zM4 6h16v10H4V6z"/></svg>
                  )}
                  {link.icon === 'bulb' && (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"/></svg>
                  )}
                </span>
                <div>
                  <h3 className="h6 mb-1">{link.title}</h3>
                  <p className="text-muted small mb-0">{link.desc}</p>
                </div>
              </div>
            </Link>
          </div>
        ))}
      </div>
    </>
  )
}
