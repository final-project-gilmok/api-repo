import { useState } from 'react'
import { useParams, Link, NavLink } from 'react-router-dom'

const tabs = [
  { to: 'policy', label: '정책 설정' },
  { to: 'monitoring', label: '모니터링 요약' },
  { to: 'ai-recommendation', label: 'AI 추천' },
]

/**
 * Render the policy configuration UI for a specific event.
 *
 * Displays tabbed navigation and forms for mandatory and advanced policies (admission RPS, token TTL, waiting room toggle, blocking rules, simple rate limit), manages local state for each input, and derives base routes from the `eventId` route parameter.
 * @returns {JSX.Element} The policy settings page for the current event.
 */
export default function PolicySettings() {
  const { eventId } = useParams()
  const base = `/admin/events/${eventId}`
  const [admissionRps, setAdmissionRps] = useState('100')
  const [tokenTtl, setTokenTtl] = useState('3600')
  const [useWaitingRoom, setUseWaitingRoom] = useState(true)
  const [blockingRules, setBlockingRules] = useState('192.168.1.10, UserAgent: BadBot')
  const [rateLimit, setRateLimit] = useState('5')

  return (
    <>
      <h1 className="h3 mb-1 fw-bold">이벤트 상세: #{eventId}</h1>
      <ul className="nav event-detail-tabs nav-tabs mb-4">
        {tabs.map((tab) => (
          <li key={tab.to} className="nav-item">
            <NavLink className="nav-link" to={`${base}/${tab.to}`} end={tab.to === 'policy'}>
              {tab.label}
            </NavLink>
          </li>
        ))}
      </ul>

      <h2 className="h5 fw-semibold mb-1">정책 설정</h2>
      <p className="text-muted mb-4">이벤트의 트래픽 제어 정책을 구성합니다.</p>

      <div className="card border rounded-3 mb-4">
        <div className="card-body">
          <h3 className="h6 fw-semibold mb-2">필수 정책 (Mandatory Policies)</h3>
          <p className="text-muted small mb-3">이벤트 운영에 필수적인 정책들을 설정합니다.</p>

          <div className="mb-3">
            <label className="form-label">입장 RPS <span className="text-danger">*</span></label>
            <input
              type="number"
              className="form-control"
              value={admissionRps}
              onChange={(e) => setAdmissionRps(e.target.value)}
              min={1}
            />
            <p className="form-text small text-muted">초당 허용되는 요청 수입니다.</p>
          </div>

          <div className="mb-3">
            <label className="form-label">토큰 TTL (초) <span className="text-danger">*</span></label>
            <input
              type="number"
              className="form-control"
              value={tokenTtl}
              onChange={(e) => setTokenTtl(e.target.value)}
              min={1}
            />
            <p className="form-text small text-muted">발급된 토큰의 유효 시간 (초)입니다.</p>
          </div>

          <div className="mb-4">
            <div className="form-check form-switch">
              <input
                className="form-check-input"
                type="checkbox"
                id="useWaitingRoom"
                checked={useWaitingRoom}
                onChange={(e) => setUseWaitingRoom(e.target.checked)}
              />
              <label className="form-check-label" htmlFor="useWaitingRoom">
                대기열 사용 (Use Waiting Room)
              </label>
            </div>
            <p className="form-text small text-muted">트래픽 초과 시 대기열 기능을 활성화합니다.</p>
          </div>

          <h3 className="h6 fw-semibold mb-2">고급 정책(선택 사항) (Advanced Policies (Optional))</h3>
          <p className="text-muted small mb-3">추가적인 트래픽 제어 및 보안 정책을 설정합니다.</p>

          <div className="mb-3">
            <label className="form-label">차단 규칙 (IP/UA) (Blocking Rules (IP/UA))</label>
            <textarea
              className="form-control"
              rows={3}
              value={blockingRules}
              onChange={(e) => setBlockingRules(e.target.value)}
              placeholder="192.168.1.10, UserAgent: BadBot"
            />
            <p className="form-text small text-muted">차단할 IP 주소 또는 사용자 에이전트 패턴을 입력합니다. 각 항목을 쉼표로 구분하세요.</p>
          </div>

          <div className="mb-4">
            <label className="form-label">간단 속도 제한 (Simple Rate Limit)</label>
            <input
              type="number"
              className="form-control"
              value={rateLimit}
              onChange={(e) => setRateLimit(e.target.value)}
              min={1}
            />
            <p className="form-text small text-muted">단일 클라이언트가 초당 허용되는 요청 수입니다.</p>
          </div>

          <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
            <span className="text-muted small">현재 버전: v1</span>
            <button type="button" className="btn btn-primary">정책 저장</button>
          </div>
        </div>
      </div>
    </>
  )
}