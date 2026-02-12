import { useParams, NavLink } from 'react-router-dom'

const base = (eventId) => eventId ? `/admin/events/${eventId}` : '/admin'
const tabs = (eventId) => [
  { to: `${base(eventId)}/policy`, label: '정책 설정' },
  { to: `${base(eventId)}/monitoring`, label: '모니터링 요약' },
  { to: `${base(eventId)}/ai-recommendation`, label: 'AI 추천' },
]

const inputSummary = [
  { label: '최근 5분 평균 RPS', value: '180 RPS' },
  { label: '최근 5분 에러율', value: '0.5%' },
  { label: '현재 큐 대기열 길이', value: '10명' },
  { label: '평균 응답 지연 (p95)', value: '120ms' },
]

const blockRules = ['IP: 192.168.1.10', 'UA: MaliciousBot']
const reasoning = '최근 트래픽 증가와 낮은 에러율을 기반으로 RPS 한도를 상향 조정하고, 세션 유지 시간을 늘려 사용자 경험을 개선합니다. 특정 IP와 User-Agent에서 비정상적인 요청 패턴이 감지되어 차단 규칙을 추가하는 것을 권장합니다.'

export default function AIRecommendation() {
  const { eventId } = useParams()
  const showTabs = !!eventId

  return (
    <>
      <h1 className="h3 mb-4 fw-bold">AI 정책 추천</h1>

      {showTabs && (
        <ul className="nav event-detail-tabs nav-tabs mb-4">
          {tabs(eventId).map((tab) => (
            <li key={tab.to} className="nav-item">
              <NavLink className="nav-link" to={tab.to}>
                {tab.label}
              </NavLink>
            </li>
          ))}
        </ul>
      )}

      <div className="row g-4">
        <div className="col-lg-5">
          <div className="card border rounded-3 h-100">
            <div className="card-body">
              <h2 className="h5 fw-semibold mb-3">입력 요약 (Input Summary)</h2>
              <p className="text-muted small mb-3">현재 시스템 지표를 기반으로 AI 추천이 생성되었습니다.</p>
              <ul className="list-unstyled mb-4">
                {inputSummary.map((item) => (
                  <li key={item.label} className="d-flex justify-content-between align-items-center py-2 border-bottom">
                    <span className="text-muted">{item.label}</span>
                    <span className="fw-semibold">{item.value}</span>
                  </li>
                ))}
              </ul>
              <button type="button" className="btn btn-primary w-100">추천 적용 (Apply Recommendation)</button>
            </div>
          </div>
        </div>

        <div className="col-lg-7">
          <div className="card border rounded-3">
            <div className="card-body">
              <h2 className="h5 fw-semibold mb-3">추천 결과 (Recommendation Results)</h2>
              <div className="row g-3 mb-3">
                <div className="col-md-6">
                  <div className="p-3 bg-light rounded">
                    <p className="text-muted small mb-1">권장 admissionRps</p>
                    <p className="fw-bold mb-0">220 RPS</p>
                  </div>
                </div>
                <div className="col-md-6">
                  <div className="p-3 bg-light rounded">
                    <p className="text-muted small mb-1">권장 tokenTtlSeconds</p>
                    <p className="fw-bold mb-0">360 초</p>
                  </div>
                </div>
              </div>
              <div className="mb-3">
                <p className="text-muted small mb-2">권장 차단 룰</p>
                <div className="d-flex flex-wrap gap-2">
                  {blockRules.map((rule) => (
                    <span key={rule} className="badge bg-secondary px-3 py-2">{rule}</span>
                  ))}
                </div>
              </div>
              <div className="mb-3">
                <p className="text-muted small mb-1">근거 (Reasoning)</p>
                <p className="small mb-0">{reasoning}</p>
              </div>
              <div className="pt-2 border-top">
                <p className="text-muted small mb-0">마지막 적용 시각: 2023-10-27 14:30:00</p>
                <p className="text-muted small mb-0">적용된 정책 버전: v1.2.3</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
