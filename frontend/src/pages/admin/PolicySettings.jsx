import { useState, useEffect } from 'react'
import { useParams, NavLink } from 'react-router-dom'
import { getPolicy, updatePolicy } from '../../api/policy.js'

const tabs = [
  { to: 'policy', label: '정책 설정' },
  { to: 'monitoring', label: '모니터링 요약' },
  { to: 'ai-recommendation', label: 'AI 추천' },
]

function blockRulesToDisplay(blockRules) {
  if (!blockRules) return ''
  const { ipPattern, userAgentPattern } = blockRules
  const parts = [ipPattern, userAgentPattern].filter(Boolean)
  return parts.join(', ')
}

function displayToBlockRules(value) {
  const trimmed = (value || '').trim()
  if (!trimmed) return { ipPattern: null, userAgentPattern: null, rateLimitKey: null }
  const parts = trimmed.split(',').map((s) => s.trim()).filter(Boolean)
  return {
    ipPattern: parts[0] || null,
    userAgentPattern: parts[1] || null,
    rateLimitKey: null,
  }
}

export default function PolicySettings() {
  const { eventId } = useParams()
  const base = `/admin/events/${eventId}`

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [policyVersion, setPolicyVersion] = useState(null)
  /** true = 저장된 정책 없음(404), 폼은 기본값으로 표시 */
  const [noPolicyYet, setNoPolicyYet] = useState(false)

  const [admissionRps, setAdmissionRps] = useState('100')
  const [admissionConcurrency, setAdmissionConcurrency] = useState('5')
  const [tokenTtl, setTokenTtl] = useState('3600')
  const [useWaitingRoom, setUseWaitingRoom] = useState(true)
  const [blockingRules, setBlockingRules] = useState('')
  const [maxRequestsPerSecond, setMaxRequestsPerSecond] = useState('100')
  const [blockDurationMinutes, setBlockDurationMinutes] = useState('10')

  useEffect(() => {
    if (!eventId) return
    setLoading(true)
    setError(null)
    setNoPolicyYet(false)
    getPolicy(eventId)
      .then((data) => {
        if (data) {
          setAdmissionRps(String(data.admissionRps ?? 100))
          setAdmissionConcurrency(String(data.admissionConcurrency ?? 5))
          setTokenTtl(String(data.tokenTtlSeconds ?? 3600))
          setBlockingRules(blockRulesToDisplay(data.blockRules))
          setMaxRequestsPerSecond(String(data.maxRequestsPerSecond ?? 100))
          setBlockDurationMinutes(String(data.blockDurationMinutes ?? 10))
          setPolicyVersion(data.policyVersion ?? null)
          setNoPolicyYet(false)
        }
      })
      .catch((e) => {
        if (e.status === 404) {
          setNoPolicyYet(true)
        } else {
          setError(e.message || '정책을 불러오지 못했습니다.')
        }
      })
      .finally(() => setLoading(false))
  }, [eventId])

  const handleSave = async (e) => {
    e.preventDefault()
    if (!eventId) return
    setSubmitting(true)
    setError(null)
    try {
      const rps = parseInt(admissionRps, 10)
      const concurrency = parseInt(admissionConcurrency, 10)
      const ttl = parseInt(tokenTtl, 10)
      const maxRps = parseInt(maxRequestsPerSecond, 10)
      const blockDur = parseInt(blockDurationMinutes, 10)
      if (Number.isNaN(rps) || rps < 0 || Number.isNaN(concurrency) || concurrency < 0 || Number.isNaN(ttl) || ttl < 0) {
        setError('RPS, 동시 접속 수, TTL은 0 이상의 숫자여야 합니다.')
        return
      }
      const version = await updatePolicy(eventId, {
        admissionRps: rps,
        admissionConcurrency: concurrency,
        tokenTtlSeconds: ttl,
        blockRules: displayToBlockRules(blockingRules),
        gateMode: null,
        maxRequestsPerSecond: Number.isNaN(maxRps) || maxRps < 0 ? null : maxRps,
        blockDurationMinutes: Number.isNaN(blockDur) || blockDur < 0 ? null : blockDur,
      })
      setPolicyVersion(version != null ? version : policyVersion)
      setNoPolicyYet(false)
    } catch (e) {
      setError(e.message || '정책 저장에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

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

      {error && (
        <div className="alert alert-danger alert-dismissible fade show" role="alert">
          {error}
          <button type="button" className="btn-close" onClick={() => setError(null)} aria-label="닫기" />
        </div>
      )}

      {loading ? (
        <p className="text-muted">불러오는 중...</p>
      ) : (
        <div className="card border rounded-3 mb-4">
          <div className="card-body">
            {noPolicyYet ? (
              <div className="alert alert-info mb-3 mb-0">
                저장된 정책이 없습니다. 아래 값을 입력한 뒤 저장하세요.
              </div>
            ) : policyVersion != null ? (
              <p className="text-muted small mb-3">
                아래는 현재 DB에 저장된 정책입니다. 수정 후 저장하면 새 버전이 적용됩니다.
              </p>
            ) : null}
            <form onSubmit={handleSave}>
              <h3 className="h6 fw-semibold mb-2">필수 정책 (Mandatory Policies)</h3>
              <p className="text-muted small mb-3">이벤트 운영에 필수적인 정책들을 설정합니다.</p>

              <div className="mb-3">
                <label className="form-label">입장 RPS <span className="text-danger">*</span></label>
                <input
                  type="number"
                  className="form-control"
                  value={admissionRps}
                  onChange={(e) => setAdmissionRps(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">초당 허용되는 요청 수입니다.</p>
              </div>

              <div className="mb-3">
                <label className="form-label">동시 접속 수 (admissionConcurrency) <span className="text-danger">*</span></label>
                <input
                  type="number"
                  className="form-control"
                  value={admissionConcurrency}
                  onChange={(e) => setAdmissionConcurrency(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">동시에 허용되는 접속 수입니다.</p>
              </div>

              <div className="mb-3">
                <label className="form-label">토큰 TTL (초) <span className="text-danger">*</span></label>
                <input
                  type="number"
                  className="form-control"
                  value={tokenTtl}
                  onChange={(e) => setTokenTtl(e.target.value)}
                  min={0}
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
                <p className="form-text small text-muted">트래픽 초과 시 대기열 기능을 활성화합니다. (UI 표시용)</p>
              </div>

              <h3 className="h6 fw-semibold mb-2">고급 정책 (선택)</h3>
              <p className="text-muted small mb-3">차단 규칙 및 매크로 방어 설정입니다.</p>

              <div className="mb-3">
                <label className="form-label">차단 규칙 (IP, UserAgent)</label>
                <textarea
                  className="form-control"
                  rows={3}
                  value={blockingRules}
                  onChange={(e) => setBlockingRules(e.target.value)}
                  placeholder="192.168.1.10, UserAgent: BadBot"
                />
                <p className="form-text small text-muted">쉼표로 구분: 첫 번째는 IP 패턴, 두 번째는 User-Agent 패턴.</p>
              </div>

              <div className="mb-3">
                <label className="form-label">유저당 초당 최대 요청 수 (maxRequestsPerSecond)</label>
                <input
                  type="number"
                  className="form-control"
                  value={maxRequestsPerSecond}
                  onChange={(e) => setMaxRequestsPerSecond(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">0이면 제한 없음.</p>
              </div>

              <div className="mb-4">
                <label className="form-label">차단 시간 (분) (blockDurationMinutes)</label>
                <input
                  type="number"
                  className="form-control"
                  value={blockDurationMinutes}
                  onChange={(e) => setBlockDurationMinutes(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">초과 시 차단되는 시간(분)입니다.</p>
              </div>

              <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <span className="text-muted small">
                  {policyVersion != null ? `현재 버전: v${policyVersion}` : '저장 후 버전이 표시됩니다.'}
                </span>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? '저장 중...' : '정책 저장'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}
