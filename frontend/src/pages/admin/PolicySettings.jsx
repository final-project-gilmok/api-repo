import { useState, useEffect, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { getPolicy, updatePolicy, getPolicyHistories, rollbackPolicy } from '../../api/policy.js'

const GATE_MODE_OPTIONS = [
  { value: 'ROUTING_ENABLED', label: '대기열 입장 사용' },
  { value: 'ROUTING_DISABLED', label: '대기열 입장 비활성화' },
]

const DEFAULT_GATE_MODE = 'ROUTING_ENABLED'

function normalizeGateMode(value) {
  return GATE_MODE_OPTIONS.some((opt) => opt.value === value) ? value : DEFAULT_GATE_MODE
}

export default function PolicySettings() {
  const { eventId } = useParams()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [policyVersion, setPolicyVersion] = useState(null)
  /** true = 저장된 정책 없음(404), 폼은 기본값으로 표시 */
  const [noPolicyYet, setNoPolicyYet] = useState(false)

  const [admissionRps, setAdmissionRps] = useState('100')
  const [admissionConcurrency, setAdmissionConcurrency] = useState('50')
  const [gateMode, setGateMode] = useState('ROUTING_ENABLED')
  const [ipPattern, setIpPattern] = useState('')
  const [userAgentPattern, setUserAgentPattern] = useState('')
  const [maxRequestsPerSecond, setMaxRequestsPerSecond] = useState('100')
  const [blockDurationMinutes, setBlockDurationMinutes] = useState('10')

  const [histories, setHistories] = useState([])
  const [historyPage, setHistoryPage] = useState(0)
  const [historyTotalPages, setHistoryTotalPages] = useState(0)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [rollingBack, setRollingBack] = useState(false)

  const loadHistories = useCallback(async (page = 0) => {
    if (!eventId) return
    setHistoryLoading(true)
    try {
      const result = await getPolicyHistories(eventId, page, 10)
      const content = Array.isArray(result.content) ? result.content : []
      content.sort((a, b) => {
        const at = a?.createdAt ? new Date(a.createdAt).getTime() : -Infinity
        const bt = b?.createdAt ? new Date(b.createdAt).getTime() : -Infinity
        return bt - at
      })
      setHistories(content)
      setHistoryTotalPages(result.totalPages ?? 0)
      setHistoryPage(page)
    } catch (e) {
      setHistories([])
      setHistoryTotalPages(0)
    } finally {
      setHistoryLoading(false)
    }
  }, [eventId])

  useEffect(() => {
    if (!eventId) return
    setLoading(true)
    setError(null)
    setNoPolicyYet(false)
    setPolicyVersion(null)
    setAdmissionRps('100')
    setAdmissionConcurrency('50')
    setGateMode('ROUTING_ENABLED')
    setIpPattern('')
    setUserAgentPattern('')
    setMaxRequestsPerSecond('100')
    setBlockDurationMinutes('10')
    getPolicy(eventId)
      .then((data) => {
        if (data) {
          setAdmissionRps(String(data.admissionRps ?? 100))
          setAdmissionConcurrency(String(data.admissionConcurrency ?? 50))
          setGateMode(normalizeGateMode(data.gateMode?.trim()))
          if (data.blockRules) {
            setIpPattern(data.blockRules.ipPattern || '')
            setUserAgentPattern(data.blockRules.userAgentPattern || '')
          }
          setMaxRequestsPerSecond(String(data.maxRequestsPerSecond ?? 100))
          setBlockDurationMinutes(String(data.blockDurationMinutes ?? 10))
          setPolicyVersion(data.policyVersion ?? null)
          setNoPolicyYet(false)
        }
      })
      .catch((e) => {
        if (e.status === 404) {
          setNoPolicyYet(true)
          setPolicyVersion(null)
        } else {
          setError(e.message || '정책을 불러오지 못했습니다.')
        }
      })
      .finally(() => setLoading(false))
  }, [eventId])

  useEffect(() => {
    if (!eventId || loading) return
    loadHistories(0)
  }, [eventId, loading, loadHistories])

  const handleRollback = async (historyId) => {
    if (!eventId || !confirm('이 시점의 정책으로 되돌리시겠습니까?')) return
    setRollingBack(true)
    setError(null)
    try {
      const policy = await rollbackPolicy(eventId, historyId)
      setAdmissionRps(String(policy.admissionRps ?? 100))
      setAdmissionConcurrency(String(policy.admissionConcurrency ?? 50))
      setGateMode(normalizeGateMode(policy.gateMode?.trim()))
      if (policy.blockRules) {
        setIpPattern(policy.blockRules.ipPattern || '')
        setUserAgentPattern(policy.blockRules.userAgentPattern || '')
      } else {
        setIpPattern('')
        setUserAgentPattern('')
      }
      setMaxRequestsPerSecond(String(policy.maxRequestsPerSecond ?? 100))
      setBlockDurationMinutes(String(policy.blockDurationMinutes ?? 10))
      setPolicyVersion(policy.policyVersion ?? null)
      setNoPolicyYet(false)
      await loadHistories(0)
    } catch (e) {
      setError(e.message || '롤백에 실패했습니다.')
    } finally {
      setRollingBack(false)
    }
  }

  const handleSave = async (e) => {
    e.preventDefault()
    if (!eventId) return
    setSubmitting(true)
    setError(null)
    try {
      const rps = parseInt(admissionRps, 10)
      const concurrency = parseInt(admissionConcurrency, 10)
      const maxRps = parseInt(maxRequestsPerSecond, 10)
      const blockDur = parseInt(blockDurationMinutes, 10)
      if (Number.isNaN(rps) || rps < 0 || Number.isNaN(concurrency) || concurrency < 0) {
        setError('RPS, 동시 접속 수는 0 이상의 숫자여야 합니다.')
        return
      }
      const result = await updatePolicy(eventId, {
        admissionRps: rps,
        admissionConcurrency: concurrency,
        blockRules: {
          ipPattern: ipPattern.trim() || null,
          userAgentPattern: userAgentPattern.trim() || null,
          rateLimitKey: null,
        },
        gateMode: normalizeGateMode(gateMode?.trim()),
        maxRequestsPerSecond: Number.isNaN(maxRps) || maxRps < 0 ? null : maxRps,
        blockDurationMinutes: Number.isNaN(blockDur) || blockDur < 0 ? null : blockDur,
      })
      const policy = result?.data ?? result
      if (policy) {
        setPolicyVersion(policy.policyVersion ?? policyVersion)
        setAdmissionRps(String(policy.admissionRps ?? rps))
        setAdmissionConcurrency(String(policy.admissionConcurrency ?? concurrency))
        setGateMode(normalizeGateMode(policy.gateMode?.trim()))
        if (policy.blockRules) {
          setIpPattern(policy.blockRules.ipPattern || '')
          setUserAgentPattern(policy.blockRules.userAgentPattern || '')
        }
        setMaxRequestsPerSecond(String(policy.maxRequestsPerSecond ?? maxRps))
        setBlockDurationMinutes(String(policy.blockDurationMinutes ?? blockDur))
      } else {
        setPolicyVersion(policyVersion)
      }
      setNoPolicyYet(false)
      await loadHistories(0)
    } catch (e) {
      setError(e.message || '정책 저장에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
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
              <h3 className="h6 fw-semibold mb-2">기본 설정</h3>
              <p className="text-muted small mb-3">이벤트 운영에 필요한 기본 값을 설정합니다.</p>

              <div className="mb-3">
                <label className="form-label">
                  입장 처리 속도 (RPS) <span className="text-danger">*</span>
                </label>
                <input
                  type="number"
                  className="form-control"
                  value={admissionRps}
                  onChange={(e) => setAdmissionRps(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">
                  입장 요청을 초당 몇 건까지 처리할지 설정합니다. 0이면 제한 없이 처리합니다.
                </p>
              </div>

              <div className="mb-3">
                <label className="form-label">
                  동시 입장 가능 수 <span className="text-danger">*</span>
                </label>
                <input
                  type="number"
                  className="form-control"
                  value={admissionConcurrency}
                  onChange={(e) => setAdmissionConcurrency(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">
                  동시에 입장 처리할 수 있는 최대 인원 수를 설정합니다. 0이면 제한이 없습니다.
                </p>
              </div>

              <div className="mb-3">
                <label className="form-label">입장 운영 방식</label>
                <select
                  className="form-select"
                  value={gateMode}
                  onChange={(e) => setGateMode(e.target.value)}
                >
                  {GATE_MODE_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
                <p className="form-text small text-muted">
                  이벤트에 대기열 입장 기능을 사용할지 여부를 선택합니다.
                </p>
              </div>

              <h3 className="h6 fw-semibold mb-2">차단 규칙</h3>
              <p className="text-muted small mb-3">특정 조건의 트래픽을 차단합니다. (패턴 입력 지원)</p>

              <div className="mb-3">
                <label className="form-label">차단 IP 패턴</label>
                <input
                  type="text"
                  className="form-control"
                  value={ipPattern}
                  onChange={(e) => setIpPattern(e.target.value)}
                  placeholder="예: 1.2.3.4 또는 ^192\.168\..*"
                />
                <p className="form-text small text-muted">
                  특정 IP(또는 대역)를 패턴으로 지정해 차단합니다. 비워두면 IP 기준 차단은 적용되지 않습니다.
                </p>
              </div>

              <div className="mb-3">
                <label className="form-label">차단 User-Agent 패턴</label>
                <input
                  type="text"
                  className="form-control"
                  value={userAgentPattern}
                  onChange={(e) => setUserAgentPattern(e.target.value)}
                  placeholder="예: BadBot|Scraper|Python-requests"
                />
                <p className="form-text small text-muted">
                  특정 브라우저/클라이언트를 패턴으로 지정해 차단합니다. 비워두면 User-Agent 기준 차단은 적용되지 않습니다.
                </p>
              </div>

              <div className="mb-3">
                <label className="form-label">사용자별 초당 최대 요청 수 (RPS)</label>
                <input
                  type="number"
                  className="form-control"
                  value={maxRequestsPerSecond}
                  onChange={(e) => setMaxRequestsPerSecond(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">
                  한 사용자가 초당 보낼 수 있는 요청 수의 상한을 설정합니다. 0이면 제한이 없습니다.
                </p>
              </div>

              <div className="mb-4">
                <label className="form-label">차단 시간 (분)</label>
                <input
                  type="number"
                  className="form-control"
                  value={blockDurationMinutes}
                  onChange={(e) => setBlockDurationMinutes(e.target.value)}
                  min={0}
                />
                <p className="form-text small text-muted">
                  차단이 적용되는 유지 시간을 설정합니다. 0이면 차단 시간을 두지 않습니다.
                </p>
              </div>

              <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <span
                  className={`badge rounded-pill px-3 py-2 ${
                    policyVersion != null ? 'text-bg-primary' : 'text-bg-light text-dark border'
                  }`}
                  style={{ fontSize: '0.95rem', fontWeight: 600 }}
                >
                  {policyVersion != null ? `현재 버전 v${policyVersion}` : '저장 후 버전이 표시됩니다'}
                </span>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? '저장 중...' : '정책 저장'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {!loading && (
        <div className="card border rounded-3 mb-4">
          <div className="card-body">
            <h3 className="h6 fw-semibold mb-3">변경 이력</h3>
            <p className="text-muted small mb-3">과거 정책 스냅샷으로 되돌릴 수 있습니다.</p>
            {historyLoading ? (
              <p className="text-muted small">이력 불러오는 중...</p>
            ) : histories.length === 0 ? (
              <p className="text-muted small mb-0">저장된 변경 이력이 없습니다.</p>
            ) : (
              <>
                <div className="table-responsive">
                  <table className="table table-sm table-hover">
                    <thead>
                      <tr>
                        <th>시점</th>
                        <th>버전</th>
                        <th>입장 처리 속도(RPS)</th>
                        <th>동시 입장 가능 수</th>
                        <th>입장 운영 방식</th>
                        <th>수정자</th>
                        <th className="text-end"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {histories.map((h) => (
                        <tr key={h.id}>
                          <td>{h.createdAt ? new Date(h.createdAt).toLocaleString('ko-KR') : '-'}</td>
                          <td>v{h.policyVersion}</td>
                          <td>{h.admissionRps}</td>
                          <td>{h.admissionConcurrency}</td>
                          <td>{h.gateMode ?? '-'}</td>
                          <td>{h.updatedByUsername ?? h.updatedByUserId ?? '-'}</td>
                          <td className="text-end">
                            <button
                              type="button"
                              className="btn btn-outline-secondary btn-sm"
                              onClick={() => handleRollback(h.id)}
                              disabled={rollingBack}
                            >
                              되돌리기
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {historyTotalPages > 1 && (
                  <nav className="mt-2 d-flex justify-content-center">
                    <ul className="pagination pagination-sm mb-0">
                      {Array.from({ length: historyTotalPages }, (_, i) => (
                        <li key={i} className={`page-item ${i === historyPage ? 'active' : ''}`}>
                          <button
                            type="button"
                            className="page-link"
                            onClick={() => loadHistories(i)}
                            disabled={historyLoading}
                          >
                            {i + 1}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </nav>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </>
  )
}
