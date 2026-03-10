import { useState, useEffect } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { api } from '../../api/client'
import SeatMap from '../../components/reservation/SeatMap'

export default function SeatSelection() {
  const { eventId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const queueKey = location.state?.queueKey || localStorage.getItem(`queueKey_${eventId}`)

  const [seats, setSeats] = useState([])
  const [selectedSeat, setSelectedSeat] = useState(null)
  const [quantity, setQuantity] = useState(1)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.get(`/events/${eventId}/seats`)
      .then((data) => setSeats(data || []))
      .catch(() => setError('좌석 정보를 불러올 수 없습니다.'))
      .finally(() => setLoading(false))
  }, [eventId])

  const handleReserve = () => {
    if (!selectedSeat || !queueKey) return
    setSubmitting(true)
    setError(null)

    api.post('/reservations', {
      eventId: Number(eventId),
      seatId: selectedSeat.seatId,
      quantity,
      queueKey,
    })
      .then((d) => {
        navigate(`/events/${eventId}/reserve/confirm`, {
          state: { reservation: d },
        })
      })
      .catch((err) => setError(err.message || '예약 요청 중 오류가 발생했습니다.'))
      .finally(() => setSubmitting(false))
  }

  if (loading) {
    return <div className="text-center py-5"><div className="spinner-border" /></div>
  }

  return (
    <>
      <h2 className="h4 fw-bold mb-3">좌석 선택</h2>

      {error && <div className="alert alert-danger">{error}</div>}

      <SeatMap
        seats={seats}
        selectedSeatId={selectedSeat?.seatId}
        onSelect={(seat) => {
            setSelectedSeat(seat)
            setQuantity(1)
            }}
      />

      {selectedSeat && (
        <div className="card mt-4 border">
          <div className="card-body">
            <h5 className="fw-semibold">{selectedSeat.section}석 선택됨</h5>
            <div className="d-flex align-items-center gap-3 mb-3">
              <label className="form-label mb-0">수량:</label>
              <select
                className="form-select"
                style={{ width: 80 }}
                value={quantity}
                onChange={(e) => setQuantity(Number(e.target.value))}
              >
                {[1, 2, 3, 4].map((n) => (
                  <option key={n} value={n} disabled={n > selectedSeat.availableCount}>
                    {n}
                  </option>
                ))}
              </select>
              <span className="fw-semibold">
                합계: {(selectedSeat.price * quantity).toLocaleString()}원
              </span>
            </div>
            <button
              className="btn btn-primary"
              onClick={handleReserve}
              disabled={submitting}
            >
              {submitting ? '처리 중...' : '예약하기'}
            </button>
          </div>
        </div>
      )}
    </>
  )
}
