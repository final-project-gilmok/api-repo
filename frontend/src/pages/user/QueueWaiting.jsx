import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'

const API_BASE = '/api'

export default function QueueWaiting() {
  const { eventId } = useParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState(null)
  const [position, setPosition] = useState(0)
  const [total, setTotal] = useState(0)
  const [eta, setEta] = useState(0)
  const [queueKey, setQueueKey] = useState(null)
  const [error, setError] = useState(null)
  const [pollInterval, setPollInterval] = useState(3000)
  const timeoutRef = useRef(null)

  // 대기열 등록
  useEffect(() => {
    const sessionKey = sessionStorage.getItem('sessionKey') || crypto.randomUUID()
    sessionStorage.setItem('sessionKey', sessionKey)

    fetch(`${API_BASE}/queue/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, sessionKey }),
    })
        .then((res) => {
            if (!res.ok) throw new Error('등록 실패')
            return res.json()
            })
      .then((data) => {
        const d = data.data || data
        setQueueKey(d.queueKey)
        setPosition(d.position)
        setEta(d.etaSeconds)
        sessionStorage.setItem(`queueKey_${eventId}`, d.queueKey)
      })
      .catch(() => setError('대기열 등록에 실패했습니다.'))
  }, [eventId])

  // 상태 폴링 (동적 간격)
  const poll = useCallback(() => {
    if (!queueKey) return
    fetch(`${API_BASE}/queue/status?eventId=${eventId}&queueKey=${queueKey}`)
        .then((res) => {
            if (!res.ok) throw new Error('상태 조회 실패')
            return res.json()
            })
      .then((data) => {
        const d = data.data || data
        setStatus(d.status)
        setPosition(d.position)
        setEta(d.etaSeconds)
        if (d.total !== undefined) setTotal(d.total)
        if (d.pollAfterMs > 0) setPollInterval(d.pollAfterMs)

        if (d.status === 'ADMITTABLE') {
          navigate(`/events/${eventId}/seats`, { state: { queueKey } })
        }
      })
  }, [queueKey, eventId, navigate])

  useEffect(() => {
    if (!queueKey) return
    poll()
    // noinspection UnnecessaryLocalVariableJS
    const schedule = () => {
      timeoutRef.current = setTimeout(() => {
        poll()
        schedule()
      }, pollInterval)
    }
    schedule()
    return () => clearTimeout(timeoutRef.current)
  }, [queueKey, pollInterval, poll])

  if (error) {
    return <div className="alert alert-danger">{error}</div>
  }

  const isSurge = total >= 5000 || eta >= 600

  return (
    <div className="text-center py-5">
      <h2 className="h4 fw-bold mb-3">대기열</h2>
      <div className="spinner-border text-primary mb-3" />
      <div className="mb-2">
        <span className="badge bg-primary fs-5 px-4 py-2">
          현재 순번: {position}
        </span>
      </div>
      {total > 0 && (
        <p className="text-muted small mb-1">
          전체 대기: {total.toLocaleString()}명
        </p>
      )}
      <p className="text-muted">
        예상 대기 시간: {eta > 0 ? `약 ${eta}초` : '곧 입장'}
      </p>
      {isSurge && (
        <div className="alert alert-warning mt-3">
          현재 접속자가 많습니다. 대기 인원 {total.toLocaleString()}명, 예상 대기 시간 약 {eta}초.
          잠시만 기다려 주세요.
        </div>
      )}
      <p className="text-muted small">
        페이지를 닫지 마세요. 순번이 되면 자동으로 좌석 선택 화면으로 이동합니다.
      </p>
    </div>
  )
}
