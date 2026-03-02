import { useEffect, useState } from 'react'
import { getMe, getDashboard } from '../../api/user'
import MyReservations from './MyReservations'

export default function MyPage() {
  const [me, setMe] = useState(null)
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let isMounted = true

    async function fetchData() {
      try {
        setError('')
        setLoading(true)

        const [meRes, dashboardRes] = await Promise.all([
          getMe(),
          getDashboard(),
        ])

        if (!isMounted) return

        setMe(meRes)
        setDashboard(dashboardRes)
      } catch (err) {
        if (!isMounted) return
        setError(err.message || '마이페이지 정보를 불러오는 중 오류가 발생했습니다.')
      } finally {
        if (isMounted) {
          setLoading(false)
        }
      }
    }

    fetchData()

    return () => {
      isMounted = false
    }
  }, [])

  if (loading) {
    return (
      <div className="text-center py-5">
        <div className="spinner-border" role="status" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="alert alert-danger" role="alert">
        {error}
      </div>
    )
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <h1 className="h4 fw-bold mb-0">마이페이지</h1>
        <p className="text-muted small mb-0">
          총 예약 <span className="fw-semibold">{dashboard?.reservationCount ?? 0}</span>건
        </p>
      </div>
      <p className="text-muted small mb-3">
        안녕하세요, <span className="fw-semibold">{me?.displayName ?? '사용자'}</span> 님
      </p>

      <div className="card">
        <div className="card-body">
          <MyReservations />
        </div>
      </div>
    </div>
  )
}

