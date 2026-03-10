import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { api } from '../../api/client'

export default function AdminSeatManagement() {
    const { eventId } = useParams()
    const [seats, setSeats] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [initRedisLoading, setInitRedisLoading] = useState(false)

    const [newSection, setNewSection] = useState('R')
    const [newTotalCount, setNewTotalCount] = useState('100')
    const [newPrice, setNewPrice] = useState('50000')

    const loadSeats = async () => {
        try {
            const data = await api.get(`/events/${eventId}/seats`)
            setSeats(Array.isArray(data) ? data : [])
        } catch (err) {
            setSeats([])
            throw err
        }
    }

    useEffect(() => {
        setLoading(true)
        setError(null)
        loadSeats()
            .catch(() => setError('좌석 목록을 불러오지 못했습니다.'))
            .finally(() => setLoading(false))
    }, [eventId])

    const handleCreateSeat = async (e) => {
        e.preventDefault()
        const section = newSection.trim()
        const totalCount = parseInt(newTotalCount, 10)
        const price = parseInt(newPrice, 10)
        if (!section || Number.isNaN(totalCount) || totalCount < 1 || Number.isNaN(price) || price < 0) {
            setError('구역명, 수량(1 이상), 가격(0 이상)을 입력하세요.')
            return
        }
        setSubmitting(true)
        setError(null)
        try {
            await api.post(`/admin/events/${eventId}/seats`, { section, totalCount, price })
        } catch {
            setError('좌석 추가 요청 중 오류가 발생했습니다.')
            setSubmitting(false)
            return
        }

        try {
            await api.post(`/admin/events/${eventId}/seats/init-redis`, {})
        } catch {
            setError('좌석은 추가되었지만 Redis 초기화에 실패했습니다. "Redis 재초기화"를 다시 시도하세요.')
        }

        try {
            await loadSeats()
        } catch {
            setError((prev) => prev ?? '좌석 목록 새로고침에 실패했습니다.')
        } finally {
            setSubmitting(false)
        }
    }

    const handleInitRedis = async () => {
        setInitRedisLoading(true)
        setError(null)
        try {
            await api.post(`/admin/events/${eventId}/seats/init-redis`, {})
        } catch {
            setError('Redis 초기화 요청 중 오류가 발생했습니다.')
            setInitRedisLoading(false)
            return
        }
        try {
            await loadSeats()
        } catch {
            setError((prev) => prev ?? '좌석 목록 새로고침에 실패했습니다.')
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
                <h1 className="h4 fw-bold mb-0">좌석 관리 - 이벤트 #{eventId}</h1>
                <Link to={`/admin/events/${eventId}`} className="btn btn-outline-secondary btn-sm">
                    돌아가기
                </Link>
            </div>

            <div className="card border rounded-3">
                <div className="card-body">
                    <p className="text-muted small mb-3">
                        이벤트에 좌석 구역을 추가하면 사용자 좌석 선택 페이지에 표시됩니다. 추가 후 Redis 재초기화를 누르면 잔여 수가 반영됩니다.
                    </p>
                    {error && (
                        <div className="alert alert-danger py-2 mb-3">{error}</div>
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

            <div className="mt-3">
                <Link to={`/admin/events/${eventId}/reservations`} className="btn btn-outline-primary btn-sm">
                    예약 현황 보기
                </Link>
            </div>
        </>
    )
}
