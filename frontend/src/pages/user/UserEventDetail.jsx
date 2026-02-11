import { useParams, useNavigate } from 'react-router-dom'

export default function UserEventDetail() {
  const { eventId } = useParams()
  const navigate = useNavigate()

  const handleEnterQueue = () => {
      if (!eventId) return
      navigate(`/events/${eventId}/queue`)
  }

  return (
    <>
      <h1 className="h3 fw-bold mb-3">이벤트 #{eventId}</h1>
      <div className="card border">
        <div className="card-body">
          <p className="text-muted mb-4">
            이 공연에 참여하려면 대기열에 입장해야 합니다.
            대기열을 통과하면 좌석을 선택하고 예약을 진행할 수 있습니다.
          </p>
            <button className="btn btn-primary btn-lg" onClick={handleEnterQueue} disabled={!eventId}>
            대기열 입장
          </button>
        </div>
      </div>
    </>
  )
}
