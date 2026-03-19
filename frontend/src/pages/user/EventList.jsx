import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api/client'

const statusLabel = { OPEN: '예매 가능', DRAFT: '준비 중', CLOSED: '종료' }

function formatPeriod(startsAt, endsAt) {
  if (!startsAt && !endsAt) return null
  const start = startsAt ? new Date(startsAt).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g,'.').replace(/\.$/, '') : '?'
  const end = endsAt ? new Date(endsAt).toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g,'.').replace(/\.$/, '') : '?'
  return `${start} ~ ${end}`
}

export default function EventList() {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get('/events')
      .then((data) => {
        // client.js에서 ApiResponse.data를 이미 풀어주기 때문에 data는 바로 배열이어야 함
        setEvents(Array.isArray(data) ? data : [])
      })
      .catch(() => setEvents([]))
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <div className="text-center py-5"><div className="spinner-border" /></div>
  }

  return (
    <>
      <h1 className="h3 fw-bold mb-2">공연 목록</h1>
      <p className="text-muted mb-4">참여하고 싶은 공연을 선택하세요.</p>

      {events.length === 0 ? (
        <div className="text-center text-muted py-5">현재 열린 공연이 없습니다.</div>
      ) : (
        <div className="row g-3">
          {events.map((evt) => (
            <div key={evt.eventId} className="col-md-6 col-lg-4">
              <div className="card h-100 border">
                <div className="card-body d-flex flex-column">
                  <h5 className="card-title fw-semibold">{evt.name ?? evt.eventName ?? `공연 #${evt.eventId}`}</h5>
                  <span className={`badge badge-status ${evt.status?.toLowerCase()} mb-2 align-self-start`}>
                    {statusLabel[evt.status] ?? evt.status}
                  </span>
                  {(evt.startsAt || evt.endsAt) && (
                    <p className="text-muted small mb-2">
                      진행 기간: {formatPeriod(evt.startsAt, evt.endsAt)}
                    </p>
                  )}
                  <div className="mt-auto">
                    <Link
                      to={`/events/${evt.eventId}`}
                      className="btn btn-primary btn-sm w-100"
                    >
                      상세 보기
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  )
}
