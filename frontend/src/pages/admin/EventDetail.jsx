import { useState, useEffect, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getEvent, openEvent, closeEvent } from '../../api/events.js'

const quickLinks = [
  { to: 'policy', title: '정책 설정', desc: '이벤트의 트래픽 제어 및 대기열 정책을 설정합니다.', icon: 'gear' },
  { to: 'seats', title: '좌석 관리', desc: '이벤트 좌석 구역을 추가하고 잔여 수를 관리합니다.', icon: 'seat' },
  { to: 'reservations', title: '예약 현황', desc: '예약 통계와 예약 내역을 확인합니다.', icon: 'list' },
  { to: 'monitoring', title: '모니터링', desc: '이벤트의 실시간 RPS, 에러율, 대기열 길이 등을 모니터링합니다.', icon: 'monitor' },
  { to: 'ai-recommendation', title: 'AI 추천', desc: 'AI가 최적의 이벤트 운영 정책을 추천하고 적용합니다.', icon: 'bulb' },
]

function toDateStr(ldt) {
  if (!ldt) return '-'
  return new Date(ldt).toISOString().slice(0, 10)
}

export default function EventDetail() {
  const { eventId } = useParams()
  const [event, setEvent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actioning, setActioning] = useState(false)

  const loadEvent = useCallback(async () => {
    if (!eventId) return
    setLoading(true)
    setError(null)
    try {
      const data = await getEvent(eventId)
      setEvent(data)
    } catch (e) {
      setError(e.message || '이벤트를 불러오지 못했습니다.')
      setEvent(null)
    } finally {
      setLoading(false)
    }
  }, [eventId])

  useEffect(() => {
    loadEvent()
  }, [loadEvent])

  const handleOpen = async () => {
    setActioning(true)
    setError(null)
    try {
      const data = await openEvent(eventId)
      if (data) setEvent(data)
    } catch (e) {
      setError(e.message || '오픈 처리에 실패했습니다.')
    } finally {
      setActioning(false)
    }
  }

  const handleClose = async () => {
    setActioning(true)
    setError(null)
    try {
      const data = await closeEvent(eventId)
      if (data) setEvent(data)
    } catch (e) {
      setError(e.message || '종료 처리에 실패했습니다.')
    } finally {
      setActioning(false)
    }
  }

  if (loading) {
    return (
      <>
        <h2 className="h5 fw-semibold mb-1">이벤트 정보</h2>
        <p className="text-muted mb-4">불러오는 중...</p>
      </>
    )
  }

  if (error && !event) {
    return (
      <>
        <h2 className="h5 fw-semibold mb-1">이벤트 정보</h2>
        <p className="text-muted mb-4">이벤트를 열기/종료하고 각 메뉴로 이동할 수 있습니다.</p>
        <div className="alert alert-danger">{error}</div>
      </>
    )
  }

  const ev = event || { eventId: eventId, name: '-', description: '-', createdAt: null, status: 'DRAFT' }

  return (
    <>
      <h2 className="h5 fw-semibold mb-1">이벤트 정보</h2>
      <p className="text-muted mb-4">이벤트를 열기/종료하고 각 메뉴로 이동할 수 있습니다.</p>
      {error && (
        <div className="alert alert-danger alert-dismissible fade show" role="alert">
          {error}
          <button type="button" className="btn-close" onClick={() => setError(null)} aria-label="닫기" />
        </div>
      )}

      <div className="card border rounded-3 mb-4">
        <div className="card-body">
          <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
            <div>
              <h2 className="h4 mb-2">{ev.name}</h2>
              <p className="text-muted small mb-1">이벤트 ID: {ev.eventId}</p>
              <p className="mb-2">{ev.description}</p>
              <p className="text-muted small mb-0">생성일: {toDateStr(ev.createdAt)}</p>
            </div>
            <div className="d-flex align-items-center gap-2">
              <span className="badge bg-light text-dark border">{ev.status}</span>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleOpen}
                disabled={actioning || ev.status === 'OPEN'}
              >
                이벤트 오픈
              </button>
              <button
                type="button"
                className="btn btn-outline-secondary btn-sm"
                onClick={handleClose}
                disabled={actioning || ev.status === 'CLOSED'}
              >
                이벤트 종료
              </button>
            </div>
          </div>
        </div>
      </div>

      <h2 className="h5 fw-semibold mb-3">이벤트 설정</h2>
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
                  {link.icon === 'seat' && (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M4 18v3c0 .55.45 1 1 1h14c.55 0 1-.45 1-1v-3c0-.55-.45-1-1-1H5c-.55 0-1 .45-1 1zm15-11V6c0-1.1-.9-2-2-2H7c-1.1 0-2 .9-2 2v1c-1.1 0-2 .9-2 2v3c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2v-3c0-1.1-.9-2-2-2zM7 6h10v1H7V6z"/></svg>
                  )}
                  {link.icon === 'list' && (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/></svg>
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
