import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { api } from '../../api/client'

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

export default function AdminReservations() {
  const { eventId } = useParams()
  const [reservations, setReservations] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)

  const loadReservationsAndStats = () => {
    return Promise.all([
      api.get(`/admin/events/${eventId}/reservations`),
      api.get(`/admin/events/${eventId}/reservations/stats`),
    ]).then(([resData, statsData]) => {
      setReservations(resData || [])
      setStats(statsData || null)
    })
  }

  useEffect(() => {
    setLoading(true)
    Promise.all([loadReservationsAndStats()]).catch(() => {}).finally(() => setLoading(false))
  }, [eventId])

  if (loading) {
    return <div className="text-center py-5"><div className="spinner-border" /></div>
  }

  return (
    <>
      <div className="d-flex align-items-center justify-content-between mb-3">
        <h1 className="h4 fw-bold mb-0">예약 현황 - 이벤트 #{eventId}</h1>
        <Link to={`/admin/events/${eventId}`} className="btn btn-outline-secondary btn-sm">
          돌아가기
        </Link>
      </div>

      {/* 예약 통계 */}
      {stats && (
        <div className="row g-3 mb-4">
          <div className="col-md-4">
            <div className="card border text-center p-3">
              <div className="text-muted small">선점 중 (HOLDING)</div>
              <div className="fs-3 fw-bold text-warning">{stats.holdingCount}</div>
              <div className="small text-muted">{stats.holdingQuantity}매</div>
            </div>
          </div>
          <div className="col-md-4">
            <div className="card border text-center p-3">
              <div className="text-muted small">확정 (CONFIRMED)</div>
              <div className="fs-3 fw-bold text-success">{stats.confirmedCount}</div>
              <div className="small text-muted">{stats.confirmedQuantity}매</div>
            </div>
          </div>
          <div className="col-md-4">
            <div className="card border text-center p-3">
              <div className="text-muted small">취소 (CANCELLED)</div>
              <div className="fs-3 fw-bold text-secondary">{stats.cancelledCount}</div>
              <div className="small text-muted">{stats.cancelledQuantity}매</div>
            </div>
          </div>
        </div>
      )}

      {/* 예약 목록 */}
      <h2 className="h5 fw-semibold mb-2">예약 내역</h2>
      {reservations.length === 0 ? (
        <div className="text-center text-muted py-5">예약 내역이 없습니다.</div>
      ) : (
        <div className="card border">
          <div className="card-body">
            <div className="table-responsive">
              <table className="table table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>예약 코드</th>
                    <th>사용자 ID</th>
                    <th>구역</th>
                    <th>수량</th>
                    <th>합계</th>
                    <th>상태</th>
                    <th>예약일</th>
                  </tr>
                </thead>
                <tbody>
                  {reservations.map((r) => (
                    <tr key={r.reservationCode}>
                      <td className="font-monospace small">{r.reservationCode?.slice(0, 8)}...</td>
                      <td>{r.userId || '-'}</td>
                      <td>{r.section}석</td>
                      <td>{r.quantity}매</td>
                      <td>{r.totalPrice?.toLocaleString()}원</td>
                      <td>
                        <span className={`badge bg-${statusColor[r.status]}`}>
                          {statusLabel[r.status]}
                        </span>
                      </td>
                      <td className="small">{r.createdAt?.slice(0, 10)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
