import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchRecentLogs } from '../../api/logs.js'

export default function Monitoring() {
  const [logs, setLogs] = useState([])
  const navigate = useNavigate()
  const { eventId } = useParams()
  const GRAFANA_BASE_URL = (import.meta.env.VITE_GRAFANA_BASE_URL || '')
      .trim()
      .replace(/\/+$/, '')

  const DASH_UID = (import.meta.env.VITE_GRAFANA_DASHBOARD_UID || '').trim()

  useEffect(() => {
    const getLogs = async () => {
      try {
        const data = await fetchRecentLogs()
        setLogs(data || [])
      } catch (error) {
        console.error('로그 조회 실패:', error)
      }
    }

    getLogs()
    const interval = setInterval(getLogs, 5000)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="d-flex flex-column gap-4">
      {eventId && (
        <div className="d-flex justify-content-end">
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={() => navigate(`/admin/events/${eventId}/ai-recommendation`)}
          >
            AI 트래픽 분석 및 정책 추천
          </button>
        </div>
      )}

      {/* Grafana 메트릭 카드 */}
      <div className="card">
        <div className="card-body">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <div>
              <h2 className="h5 fw-semibold mb-1">시스템 메트릭 (Grafana)</h2>
              <p className="text-muted small mb-0">
                주요 트래픽, 에러율, 지연 시간 등을 실시간으로 모니터링합니다.
              </p>
            </div>
          </div>
          <div className="border rounded overflow-hidden">
            {GRAFANA_BASE_URL&& DASH_UID ? (
                <iframe
                    src={`${GRAFANA_BASE_URL}/d/${DASH_UID}/jvm-micrometer?orgId=1&kiosk&refresh=30s`}
                    width="100%"
                    height="480"
                    frameBorder="0"
                    title="Grafana Dashboard"
                />
            ) : (
                <div className="text-center text-muted py-5">
                  Grafana URL/UID가 설정되지 않았습니다.
                </div>
            )}
          </div>
        </div>
      </div>

      {/* 최근 API 로그 카드 */}
      <div className="card">
        <div className="card-body">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <div>
              <h2 className="h5 fw-semibold mb-1">최신 API 요청 로그 (Top 100)</h2>
              <p className="text-muted small mb-0">
                최근 수집된 API 요청 이력입니다. 상태 코드와 응답 시간을 함께 확인할 수 있습니다.
              </p>
            </div>
          </div>
          <div className="table-responsive">
            <table className="table table-hover align-middle">
              <thead className="table-light">
                <tr>
                  <th>요청 시간</th>
                  <th>메서드</th>
                  <th>경로 (Path)</th>
                  <th>상태 코드</th>
                  <th>처리 시간</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td className="small">{new Date(log.timestamp).toLocaleString()}</td>
                    <td className="fw-semibold">
                      <span
                        className={`badge text-bg-${
                          log.method === 'GET'
                            ? 'primary'
                            : log.method === 'POST'
                              ? 'success'
                              : 'secondary'
                        }`}
                      >
                        {log.method}
                      </span>
                    </td>
                    <td className="small font-monospace">{log.path}</td>
                    <td>
                      <span
                        className={`badge fw-semibold text-bg-${
                          log.status >= 500
                            ? 'danger'
                            : log.status >= 400
                              ? 'warning'
                              : 'success'
                        }`}
                      >
                        {log.status}
                      </span>
                    </td>
                    <td className="small">{log.latencyMs} ms</td>
                  </tr>
                ))}
                {logs.length === 0 && (
                  <tr>
                    <td colSpan={5} className="text-center text-muted py-4 small">
                      표시할 로그가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}

