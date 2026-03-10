import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api } from '../../api/client'

export default function QueueWaiting() {
  const { eventId } = useParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState(null)
  const [position, setPosition] = useState(0)
  const [total, setTotal] = useState(0)
  const [eta, setEta] = useState(0)
  const [queueKey, setQueueKey] = useState(null)
  const [error, setError] = useState(null)
  const pollIntervalRef = useRef(3000)
  const timeoutRef = useRef(null)

  const storageKey = `queueKey_${eventId}`
  const initFlag = `tab_initialized_${eventId}`

  // 새로 등록하고 localStorage에 저장
  const registerNew = useCallback(() => {
    return api.post('/queue/register', { eventId })
      .then((d) => {
        setQueueKey(d.queueKey)
        setPosition(d.position)
        setEta(d.etaSeconds)
        localStorage.setItem(storageKey, d.queueKey)
        sessionStorage.setItem(initFlag, 'true')
      })
      .catch(() => setError('예약 입장에 실패했습니다.'))
  }, [eventId, storageKey, initFlag])

  // 대기열 등록 (새 탭 vs 새로고침 판별)
  useEffect(() => {
    const isRefresh = sessionStorage.getItem(initFlag) === 'true'

    if (isRefresh) {
      // 새로고침 → 항상 새로 등록
      registerNew()
    } else {
      // 새 탭 → localStorage에서 기존 queueKey 확인
      const existing = localStorage.getItem(storageKey)
      if (existing) {
        // 기존 queueKey가 유효한지 확인
        api.get(`/queue/status?eventId=${eventId}`, { 'X-Queue-Key': existing })
          .then((d) => {
            if (d.status === 'EXPIRED') {
              // 만료됨 → 새로 등록
              registerNew()
            } else {
              // 유효함 → 공유
              setQueueKey(existing)
              setPosition(d.position)
              setEta(d.etaSeconds)
              if (d.total !== undefined) setTotal(d.total)
              sessionStorage.setItem(initFlag, 'true')
            }
          })
          .catch(() => registerNew())
      } else {
        registerNew()
      }
    }
  }, [eventId, storageKey, initFlag, registerNew])

  // 다른 탭에서 queueKey가 변경되면 동기화
  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === storageKey && e.newValue && e.newValue !== queueKey) {
        setQueueKey(e.newValue)
        setPosition(0)
        setEta(0)
        setTotal(0)
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [storageKey, queueKey])

  // 상태 폴링 (동적 간격)
  const poll = useCallback(() => {
    if (!queueKey) return
    api.get(`/queue/status?eventId=${eventId}`, { 'X-Queue-Key': queueKey })
      .then((d) => {
        setStatus(d.status)
        setPosition(d.position)
        setEta(d.etaSeconds)
        if (d.total !== undefined) setTotal(d.total)
        if (d.pollAfterMs > 0) pollIntervalRef.current = d.pollAfterMs

        if (d.status === 'ADMITTABLE') {
          // 입장 가능 상태면 좌석 선택 페이지로 이동 (토큰은 나중에 쿠키로 발급됨)
          navigate(`/events/${eventId}/seats`, { state: { queueKey } })
        }
      })
  }, [queueKey, eventId, navigate])

  useEffect(() => {
    if (!queueKey) return
    poll()
    const schedule = () => {
      timeoutRef.current = setTimeout(() => {
        poll()
        schedule()
      }, pollIntervalRef.current)
    }
    schedule()
    return () => clearTimeout(timeoutRef.current)
  }, [queueKey, poll])

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
      <div className="alert alert-danger mt-3">
        <strong>주의:</strong> 페이지를 새로고침하면 대기 순번이 초기화됩니다. 새로고침하지 마세요!
      </div>
      <p className="text-muted small">
        페이지를 닫지 마세요. 순번이 되면 자동으로 좌석 선택 화면으로 이동합니다.
      </p>
    </div>
  )
}
