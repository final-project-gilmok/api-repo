import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'

const API_BASE = '/api'

export default function EventList() {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetch(`${API_BASE}/events`)
      .then((res) => res.json())
      .then((data) => setEvents(data.data || []))
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
        <div className="text-center text-muted py-5">현재 열린 이벤트가 없습니다.</div>
      ) : (
        <div className="row g-3">
          {events.map((evt) => (
            <div key={evt.eventId} className="col-md-6 col-lg-4">
              <div className="card h-100 border">
                <div className="card-body d-flex flex-column">
                  <h5 className="card-title fw-semibold">{evt.name || `이벤트 #${evt.eventId}`}</h5>
                  <span className={`badge badge-status ${evt.status?.toLowerCase()} mb-2 align-self-start`}>
                    {evt.status}
                  </span>
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
