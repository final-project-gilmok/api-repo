import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { api } from '../../api/client'

const HOLD_SECONDS = 300

export default function ReservationConfirm() {
  const { eventId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const reservation = location.state?.reservation

  const calcRemaining = () => {
    if (!reservation?.createdAt) return HOLD_SECONDS
    const elapsed = Math.floor((Date.now() - new Date(reservation.createdAt).getTime()) / 1000)
    return Math.max(0, HOLD_SECONDS - elapsed)
  }

  const [remaining, setRemaining] = useState(calcRemaining)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState(null)
  const intervalRef = useRef(null)

  useEffect(() => {
    if (!reservation) return

    setRemaining(calcRemaining())

    intervalRef.current = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(intervalRef.current)
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(intervalRef.current)
  }, [reservation])

  if (!reservation) {
    return (
      <div className="alert alert-warning">
        예약 정보가 없습니다. <a href={`/events/${eventId}/seats`}>좌석 선택으로 돌아가기</a>
      </div>
    )
  }

  const handleConfirm = () => {
    setConfirming(true)
    setError(null)

    api.post(`/reservations/${reservation.reservationCode}/confirm`)
      .then((d) => {
        navigate(`/reservations/${reservation.reservationCode}`, {
          state: { reservation: d },
        })
      })
      .catch((err) => {
        if (err.status === 403) {
          setError('입장 권한이 없거나 만료되었습니다. 대기열을 다시 거쳐주세요.');
        } else {
          setError(err.message || '확정 요청 중 오류가 발생했습니다.');
        }
      })
      .finally(() => setConfirming(false))
  }

  const handleCancel = () => {
    api.delete(`/reservations/${reservation.reservationCode}`)
      .then(() => navigate(`/events/${eventId}/seats`))
      .catch(() => setError('취소 실패'))
  }

  const minutes = Math.floor(remaining / 60)
  const seconds = remaining % 60

  return (
    <>
      <h2 className="h4 fw-bold mb-3">예약 확인</h2>

      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card border mb-3">
        <div className="card-body">
          <div className="text-center mb-3">
            <span className={`fs-3 fw-bold ${remaining <= 60 ? 'text-danger' : 'text-primary'}`}>
              {String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}
            </span>
            <div className="text-muted small">남은 시간 내에 확정해주세요</div>
          </div>

          <table className="table mb-0">
            <tbody>
              <tr><th>예약 코드</th><td>{reservation.reservationCode}</td></tr>
              <tr><th>이벤트</th><td>{reservation.eventName}</td></tr>
              <tr><th>구역</th><td>{reservation.section}석</td></tr>
              <tr><th>수량</th><td>{reservation.quantity}매</td></tr>
              <tr><th>단가</th><td>{reservation.price?.toLocaleString()}원</td></tr>
              <tr><th className="fw-bold">합계</th><td className="fw-bold">{reservation.totalPrice?.toLocaleString()}원</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="d-flex gap-2">
        <button
          className="btn btn-primary flex-grow-1"
          onClick={handleConfirm}
          disabled={confirming || remaining <= 0}
        >
          {confirming ? '처리 중...' : '예약 확정'}
        </button>
        <button className="btn btn-outline-secondary" onClick={handleCancel}>
          취소
        </button>
      </div>

      {remaining <= 0 && (
        <div className="alert alert-warning mt-3">
          선점 시간이 만료되었습니다. 좌석 선택부터 다시 진행해주세요.
        </div>
      )}
    </>
  )
}
