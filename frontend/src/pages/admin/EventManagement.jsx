import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { getEvents, createEvent } from '../../api/events.js'

const statusLabel = { OPEN: 'OPEN', DRAFT: 'DRAFT', CLOSED: 'CLOSED' }
const statusClass = { OPEN: 'open', DRAFT: 'draft', CLOSED: 'closed' }

// TODO: api-repo policy/constants/PolicyDefaults.java 와 값 동기화 필요.
const POLICY_DEFAULT_LABEL = {
  admissionRps: 'Admission RPS',
  admissionConcurrency: 'Admission Concurrency',
  tokenTtlSeconds: 'Token TTL (초)',
  maxRequestsPerSecond: 'Max RPS',
  blockDurationMinutes: 'Block Duration (분)',
  gateMode: 'Gate Mode',
  blockRules: 'Block Rules',
}
const POLICY_DEFAULT_VALUES = {
  admissionRps: 0,
  admissionConcurrency: 0,
  tokenTtlSeconds: 300,
  maxRequestsPerSecond: 100,
  blockDurationMinutes: 10,
  gateMode: 'ROUTING_ENABLED',
  blockRules: '없음 (필요 시 정책 설정에서 추가)',
}

function toDateStr(ldt) {
  if (!ldt) return ''
  const d = new Date(ldt)
  return d.toISOString().slice(0, 10)
}

export default function EventManagement() {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [policyDefaultOpen, setPolicyDefaultOpen] = useState(false)

  const loadEvents = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getEvents()
      setEvents(Array.isArray(data) ? data : [])
    } catch (e) {
      setError(e.message || '목록을 불러오지 못했습니다.')
      setEvents([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadEvents()
  }, [])

  const handleCreate = async (e) => {
    e.preventDefault()
    const trimmedName = name.trim()
    const trimmedDesc = description.trim()
    if (!trimmedName) return
    const start = startsAt ? new Date(startsAt).toISOString() : new Date().toISOString()
    const end = endsAt ? new Date(endsAt).toISOString() : new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
    setSubmitting(true)
    setError(null)
    try {
      await createEvent({
        name: trimmedName,
        description: trimmedDesc || ' ',
        startsAt: start,
        endsAt: end,
        policy: {}, // 백엔드 PolicyDefaults 적용
      })
      setName('')
      setDescription('')
      setStartsAt('')
      setEndsAt('')
      await loadEvents()
    } catch (err) {
      setError(err.message || '이벤트 생성에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <h1 className="h3 mb-1 fw-bold">이벤트 관리</h1>
      <p className="text-muted mb-4">
        운영할 데모 이벤트를 생성하고 목록을 확인하세요. 각 이벤트의 상세 설정 페이지로 이동할 수 있습니다.
      </p>

      {error && (
        <div className="alert alert-danger alert-dismissible fade show" role="alert">
          {error}
          <button type="button" className="btn-close" onClick={() => setError(null)} aria-label="닫기" />
        </div>
      )}

      <div className="row g-4">
        <div className="col-lg-7">
          <div className="card border rounded-3">
            <div className="card-body">
              <h2 className="h5 fw-semibold mb-1">이벤트 목록</h2>
              <p className="text-muted small mb-3">현재 생성된 모든 이벤트를 확인하고 관리합니다.</p>
              {loading ? (
                <p className="text-muted mb-0">불러오는 중...</p>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead>
                      <tr>
                        <th>이벤트 ID</th>
                        <th>이름</th>
                        <th>상태</th>
                        <th>생성일</th>
                        <th className="text-end" style={{ width: 48 }}></th>
                      </tr>
                    </thead>
                    <tbody>
                      {events.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="text-muted text-center py-4">
                            이벤트가 없습니다.
                          </td>
                        </tr>
                      ) : (
                        events.map((evt) => (
                          <tr key={evt.eventId}>
                            <td className="fw-medium">{evt.eventId}</td>
                            <td>{evt.name}</td>
                            <td>
                              <span className={`badge badge-status ${statusClass[evt.status] || 'draft'}`}>
                                {statusLabel[evt.status] ?? evt.status}
                              </span>
                            </td>
                            <td>{toDateStr(evt.createdAt)}</td>
                            <td className="text-end">
                              <Link
                                to={`/admin/events/${evt.eventId}`}
                                className="btn btn-sm btn-link text-secondary p-0"
                                title="상세"
                                aria-label="이벤트 상세 보기"
                              >
                                ⋮
                              </Link>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="col-lg-5">
          <div className="card border rounded-3">
            <div className="card-body">
              <h2 className="h5 fw-semibold mb-1">새 이벤트 생성</h2>
              <p className="text-muted small mb-3">새로운 이벤트를 생성하고 데모 운영을 준비합니다.</p>
              <form onSubmit={handleCreate}>
                <div className="mb-3">
                  <label htmlFor="event-name" className="form-label">이벤트 이름 <span className="text-danger">*</span></label>
                  <input
                    id="event-name"
                    type="text"
                    className="form-control"
                    placeholder="예: 2026년 신년 맞이 프로모션"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                </div>
                <div className="mb-3">
                  <label htmlFor="event-description" className="form-label">이벤트 설명 <span className="text-danger">*</span></label>
                  <textarea
                    id="event-description"
                    className="form-control"
                    rows={4}
                    placeholder="이벤트에 대한 자세한 설명을 입력하세요."
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                  <p className="form-text small text-muted">백엔드 필수값이므로 비우면 공백으로 전송됩니다.</p>
                </div>
                <div className="mb-3">
                  <label htmlFor="event-startsAt" className="form-label">시작 일시</label>
                  <input
                    id="event-startsAt"
                    type="datetime-local"
                    className="form-control"
                    value={startsAt}
                    onChange={(e) => setStartsAt(e.target.value)}
                  />
                </div>
                <div className="mb-3">
                  <label htmlFor="event-endsAt" className="form-label">종료 일시</label>
                  <input
                    id="event-endsAt"
                    type="datetime-local"
                    className="form-control"
                    value={endsAt}
                    onChange={(e) => setEndsAt(e.target.value)}
                  />
                  <p className="form-text small text-muted">비우면 기본값(현재/7일 후)으로 전송됩니다.</p>
                </div>

                <div className="mb-3">
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-2"
                    onClick={() => setPolicyDefaultOpen((o) => !o)}
                    aria-expanded={policyDefaultOpen}
                  >
                    {policyDefaultOpen ? '▼' : '▶'} 기본 정책 (PolicyDefault)
                  </button>
                  {policyDefaultOpen && (
                    <div className="small text-muted border rounded p-3 mt-2 bg-light">
                      <div className="row g-2">
                        {Object.entries(POLICY_DEFAULT_LABEL).map(([key, label]) => (
                          <div
                            key={key}
                            className={key === 'blockRules' ? 'col-12' : 'col-6 col-md-4'}
                          >
                            {label}: {POLICY_DEFAULT_VALUES[key]}
                          </div>
                        ))}
                      </div>
                      <p className="mb-0 mt-2 small">이벤트 생성 시 위 기본값이 적용됩니다. 생성 후 정책 설정에서 변경할 수 있습니다.</p>
                    </div>
                  )}
                </div>

                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? '생성 중...' : '이벤트 생성'}
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
