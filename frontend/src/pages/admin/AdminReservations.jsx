import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'

const API_BASE = '/api'

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
  const [seats, setSeats] = useState([])
  const [loading, setLoading] = useState(true)
  const [seatError, setSeatError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [initRedisLoading, setInitRedisLoading] = useState(false)

  const [newSection, setNewSection] = useState('R')
  const [newTotalCount, setNewTotalCount] = useState('100')
  const [newPrice, setNewPrice] = useState('50000')

  const loadReservationsAndStats = () => {
    return Promise.all([
      fetch(`${API_BASE}/admin/events/${eventId}/reservations`).then((r) => r.json()),
      fetch(`${API_BASE}/admin/events/${eventId}/reservations/stats`).then((r) => r.json()),
    ]).then(([resData, statsData]) => {
      setReservations(resData.data || [])
      setStats(statsData.data || null)
    })
  }

  const loadSeats = () => {
    return fetch(`${API_BASE}/events/${eventId}/seats`)
      .then((r) => r.json())
      .then((data) => setSeats(data.data ?? []))
      .catch(() => setSeats([]))
  }

  useEffect(() => {
    setLoading(true)
    setSeatError(null)
    Promise.all([loadReservationsAndStats(), loadSeats()]).catch(() => {}).finally(() => setLoading(false))
  }, [eventId])

  const handleCreateSeat = async (e) => {
    e.preventDefault()
    const section = newSection.trim()
    const totalCount = parseInt(newTotalCount, 10)
    const price = parseInt(newPrice, 10)
    if (!section || Number.isNaN(totalCount) || totalCount < 1 || Number.isNaN(price) || price < 0) {
      setSeatError('구역명, 수량(1 이상), 가격(0 이상)을 입력하세요.')
      return
    }
    setSubmitting(true)
    setSeatError(null)
    try {
      const res = await fetch(`${API_BASE}/admin/events/${eventId}/seats`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ section, totalCount, price }),
      })
      const data = await res.json()
      if (data.status !== 'success') {
        setSeatError(data.message || '좌석 추가에 실패했습니다.')
        return
      }
      const redisRes = await fetch(`${API_BASE}/admin/events/${eventId}/seats/init-redis`, { method: 'POST' })
      const redisData = await redisRes.json()
      if (redisData.status !== 'success') {
        setSeatError(redisData.message || 'Redis 초기화에 실패했습니다.')
      }
      await loadSeats()
    } catch {
      setSeatError('좌석 추가 요청 중 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const handleInitRedis = async () => {
    setInitRedisLoading(true)
    setSeatError(null)
    try {
      const res = await fetch(`${API_BASE}/admin/events/${eventId}/seats/init-redis`, { method: 'POST' })
      const data = await res.json()
      if (data.status !== 'success') setSeatError(data.message || 'Redis 초기화에 실패했습니다.')
      else await loadSeats()
    } catch {
      setSeatError('Redis 초기화 요청 중 오류가 발생했습니다.')
    } finally {
      setInitRedisLoading(false)
    }
  }

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

      {/* 좌석 관리 */}
      <div className="card border rounded-3 mb-4">
        <div className="card-body">
          <h2 className="h5 fw-semibold mb-3">좌석 관리</h2>
          <p className="text-muted small mb-3">
            이벤트에 좌석 구역을 추가하면 사용자 좌석 선택 페이지에 표시됩니다. 추가 후 Redis 재초기화를 누르면 잔여 수가 반영됩니다.
          </p>
          {seatError && (
            <div className="alert alert-danger py-2 mb-3">{seatError}</div>
          )}
          <div className="row align-items-end g-3 mb-4">
            <div className="col-auto">
              <label className="form-label small mb-0">구역명</label>
              <input
                type="text"
                className="form-control form-control-sm"
                placeholder="R"
                value={newSection}
                onChange={(e) => setNewSection(e.target.value)}
                style={{ width: 80 }}
              />
            </div>
            <div className="col-auto">
              <label className="form-label small mb-0">수량</label>
              <input
                type="number"
                className="form-control form-control-sm"
                min={1}
                value={newTotalCount}
                onChange={(e) => setNewTotalCount(e.target.value)}
                style={{ width: 100 }}
              />
            </div>
            <div className="col-auto">
              <label className="form-label small mb-0">가격(원)</label>
              <input
                type="number"
                className="form-control form-control-sm"
                min={0}
                value={newPrice}
                onChange={(e) => setNewPrice(e.target.value)}
                style={{ width: 120 }}
              />
            </div>
            <div className="col-auto">
              <button
                type="button"
                className="btn btn-primary btn-sm"
                onClick={handleCreateSeat}
                disabled={submitting}
              >
                {submitting ? '추가 중...' : '좌석 구역 추가'}
              </button>
            </div>
            <div className="col-auto">
              <button
                type="button"
                className="btn btn-outline-secondary btn-sm"
                onClick={handleInitRedis}
                disabled={initRedisLoading || seats.length === 0}
              >
                {initRedisLoading ? '처리 중...' : 'Redis 재초기화'}
              </button>
            </div>
          </div>
          {seats.length === 0 ? (
            <p className="text-muted small mb-0">등록된 좌석이 없습니다. 위에서 구역을 추가하세요.</p>
          ) : (
            <div className="table-responsive">
              <table className="table table-sm table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>구역</th>
                    <th>총 수량</th>
                    <th>잔여</th>
                    <th>가격</th>
                  </tr>
                </thead>
                <tbody>
                  {seats.map((s) => (
                    <tr key={s.seatId}>
                      <td className="fw-medium">{s.section}석</td>
                      <td>{s.totalCount}</td>
                      <td>{s.availableCount}</td>
                      <td>{s.price?.toLocaleString()}원</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
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
