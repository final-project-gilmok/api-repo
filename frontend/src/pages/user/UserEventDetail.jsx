import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api } from '../../api/client'

function formatPeriod(startsAt, endsAt) {
  if (!startsAt && !endsAt) return null
  const start = startsAt ? new Date(startsAt).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').replace(/\.$/, '') : '?'
  const end = endsAt ? new Date(endsAt).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '.').replace(/\.$/, '') : '?'
  return `${start} ~ ${end}`
}

const statusLabel = { OPEN: '예매 가능', DRAFT: '준비 중', CLOSED: '종료' }

export default function UserEventDetail() {
  const { eventId } = useParams()
  const navigate = useNavigate()
  const [event, setEvent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    if (!eventId) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    api
      .get('/events')
      .then((data) => {
        if (cancelled) return
        const list = Array.isArray(data) ? data : []
        const found = list.find((e) => String(e.eventId) === String(eventId))
        setEvent(found || null)
        if (!found) setError('이벤트를 찾을 수 없거나 현재 예매 가능한 상태가 아닙니다.')
      })
      .catch(() => {
        if (cancelled) return
        setEvent(null)
        setError('이벤트 정보를 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [eventId])

  const handleEnterQueue = () => {
    if (!eventId || event?.status !== 'OPEN') return
    navigate(`/events/${eventId}/queue`)
  }

  if (loading) {
    return (
      <div className="text-center py-5">
        <div className="spinner-border" role="status" aria-label="로딩 중" />
      </div>
    )
  }

  if (error || !event) {
    return (
      <div className="alert alert-warning">
        {error || '이벤트를 찾을 수 없습니다.'}
      </div>
    )
  }

  const period = formatPeriod(event.startsAt, event.endsAt)

  return (
    <>
      <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
        <h1 className="h3 fw-bold mb-0">{event.name || `이벤트 #${eventId}`}</h1>
        <span className={`badge badge-status ${(event.status || '').toLowerCase()}`}>
          {statusLabel[event.status] ?? event.status}
        </span>
      </div>

      {(period || event.description) && (
        <div className="card border mb-4">
          <div className="card-body">
            {period && (
              <p className="text-muted small mb-2">
                <span className="fw-semibold">진행 기간</span> {period}
              </p>
            )}
            {event.description && (
              <p className="text-muted small mb-0">{event.description}</p>
            )}
          </div>
        </div>
      )}

      <div className="card border">
        <div className="card-body">
          <p className="text-muted mb-4">
            이 공연에 참여하려면 대기열에 입장해야 합니다.
            대기열을 통과하면 좌석을 선택하고 예약을 진행할 수 있습니다.
          </p>
          <button
            className="btn btn-primary btn-lg"
            onClick={handleEnterQueue}
            disabled={event.status !== 'OPEN'}
          >
            대기열 입장
          </button>
          {event.status !== 'OPEN' && (
            <p className="text-muted small mt-2 mb-0">예매 가능 상태(OPEN)일 때만 입장할 수 있습니다.</p>
          )}
        </div>
      </div>
    </>
  )
}
