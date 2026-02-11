import { useParams, useLocation, Link } from 'react-router-dom'

export default function ReservationResult() {
  const { code } = useParams()
  const location = useLocation()
  const reservation = location.state?.reservation

  if (!reservation) {
    return (
      <div className="alert alert-warning">
        예약 정보가 없습니다. <Link to="/my-reservations">내 예약 목록</Link>에서 확인하세요.
      </div>
    )
  }

  return (
    <div className="text-center py-5">
      <div className="mb-3">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="#198754">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
        </svg>
      </div>
      <h2 className="h3 fw-bold text-success mb-3">예약이 확정되었습니다!</h2>

      <div className="card border mx-auto" style={{ maxWidth: 480 }}>
        <div className="card-body text-start">
          <table className="table mb-0">
            <tbody>
              <tr><th>예약 코드</th><td className="font-monospace">{reservation.reservationCode}</td></tr>
              <tr><th>이벤트</th><td>{reservation.eventName}</td></tr>
              <tr><th>구역</th><td>{reservation.section}석</td></tr>
              <tr><th>수량</th><td>{reservation.quantity}매</td></tr>
              <tr><th>합계</th><td className="fw-bold">{reservation.totalPrice?.toLocaleString()}원</td></tr>
              <tr><th>상태</th><td><span className="badge bg-success">{reservation.status}</span></td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="mt-4 d-flex justify-content-center gap-2">
        <Link to="/my-reservations" className="btn btn-outline-primary">내 예약 목록</Link>
        <Link to="/" className="btn btn-primary">홈으로</Link>
      </div>
    </div>
  )
}
