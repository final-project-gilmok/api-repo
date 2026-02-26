import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'

const API_BASE = ''

const statusLabel = {
  HOLDING: '선점 중',
  CONFIRMED: '확정',
  CANCELLED: '취소됨',
}
const statusColor = {
  HOLDING: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'secondary',
}

export default function MyReservations() {
  const [reservations, setReservations] = useState([])
  const [loading, setLoading] = useState(true)

  const userId = sessionStorage.getItem('userId') || '1'

  useEffect(() => {
    fetch(`${API_BASE}/reservations/my`, {
      headers: { 'X-User-Id': userId },
    })
      .then((res) => res.json())
      .then((data) => setReservations(data.data || []))
      .catch(() => setReservations([]))
      .finally(() => setLoading(false))
  }, [userId])

  const handleCancel = (code) => {
    if (!confirm('예약을 취소하시겠습니까?')) return

    fetch(`${API_BASE}/reservations/${code}`, {
      method: 'DELETE',
      headers: { 'X-User-Id': userId },
    })
      .then((res) => res.json())
      .then((data) => {
          if (data.status === 'error') {
              alert(data.message || '취소에 실패했습니다.')
              return }
        if (data.data) {
          setReservations((prev) =>
            prev.map((r) => (r.reservationCode === code ? data.data : r))
          )
        }
      })
        .catch(() => alert('취소 요청 중 오류가 발생했습니다.'))
  }

  if (loading) {
    return <div className="text-center py-5"><div className="spinner-border" /></div>
  }

  return (
    <>
      <h2 className="h4 fw-bold mb-3">내 예약 목록</h2>

      {reservations.length === 0 ? (
        <div className="text-center text-muted py-5">
          예약 내역이 없습니다. <Link to="/">공연 목록 보기</Link>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="table table-hover align-middle">
            <thead>
              <tr>
                <th>예약 코드</th>
                <th>이벤트</th>
                <th>구역</th>
                <th>수량</th>
                <th>합계</th>
                <th>상태</th>
                <th>예약일</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {reservations.map((r) => (
                <tr key={r.reservationCode}>
                  <td className="font-monospace small">{r.reservationCode.slice(0, 8)}...</td>
                  <td>{r.eventName}</td>
                  <td>{r.section}석</td>
                  <td>{r.quantity}매</td>
                  <td>{r.totalPrice?.toLocaleString()}원</td>
                  <td>
                    <span className={`badge bg-${statusColor[r.status]}`}>
                      {statusLabel[r.status]}
                    </span>
                    {r.status === 'CANCELLED' && r.cancelledAt && (
                      <div className="text-muted small mt-1">
                        {r.cancelledAt.replace('T', ' ').slice(0, 16)}
                      </div>
                    )}
                  </td>
                  <td className="small">{r.createdAt?.slice(0, 10)}</td>
                  <td>
                    {r.status !== 'CANCELLED' && (
                      <button
                        className="btn btn-sm btn-outline-danger"
                        onClick={() => handleCancel(r.reservationCode)}
                      >
                        취소
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
