import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import { getPolicy } from '../../api/policy'

const base = (eventId) => (eventId ? `/admin/events/${eventId}` : '/admin')

export default function AIRecommendation() {
    const { eventId } = useParams()
    const navigate = useNavigate()

    const [loading, setLoading] = useState(false)
    const [applying, setApplying] = useState(false)
    const [aiData, setAiData] = useState(null)

    const handleRequestAi = async () => {
        if (!eventId) return
        setLoading(true)
        try {
            // 백엔드 엔드포인트: POST /admin/events/{eventId}/recommendation
            const data = await api.post(`/admin/events/${eventId}/recommendation`, {})
            setAiData(data)
        } catch (error) {
            console.error('AI 분석 실패:', error)
            alert('AI 분석 중 오류가 발생했습니다.')
        } finally {
            setLoading(false)
        }
    }

    const handleApply = async () => {
        if (!aiData || !eventId) return
        if (!window.confirm('AI가 추천한 이 정책을 실제 서버에 즉시 적용하시겠습니까?')) return

        setApplying(true)
        try {
            const suggested = aiData.suggestedBlockRules || {}
            const blockRules =
                suggested && (suggested.ipRanges?.length > 0 || suggested.userAgentPatterns)
                    ? {
                        // 백엔드 PolicyUpdateRequest.BlockRules(ipPattern, userAgentPattern, rateLimitKey)에 맞춰 변환
                        ipPattern:
                            Array.isArray(suggested.ipRanges) && suggested.ipRanges.length > 0
                                ? suggested.ipRanges.join(',')
                                : null,
                        userAgentPattern: suggested.userAgentPatterns || null,
                        rateLimitKey: null,
                    }
                    : null

            // AI가 동시 접속 수를 주지 않으면 기존 정책 값을 보존 (덮어쓰지 않음)
            let admissionConcurrency = aiData.recommendedAdmissionConcurrency
            if (admissionConcurrency === undefined || admissionConcurrency === null) {
                try {
                    const currentPolicy = await getPolicy(eventId)
                    admissionConcurrency = currentPolicy?.admissionConcurrency ?? 50
                } catch {
                    admissionConcurrency = 50
                }
            }

            const updateRequest = {
                admissionRps: aiData.recommendedAdmissionRps,
                admissionConcurrency,
                blockRules,
            }

            // 백엔드 엔드포인트: PUT /admin/events/{eventId}/policy
            await api.put(`/admin/events/${eventId}/policy`, updateRequest)
            alert('추천 정책이 성공적으로 적용되었습니다.')
            navigate(`${base(eventId)}/policy`)
        } catch (error) {
            console.error('정책 적용 실패:', error)
            alert('정책 적용에 실패했습니다.')
        } finally {
            setApplying(false)
        }
    }

    return (
        <div className="d-flex flex-column gap-4">
            {/* 상단 헤더 영역 */}
            <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <div>
                    <h1 className="h3 fw-bold mb-1">AI 정책 추천</h1>
                    <p className="text-muted small mb-0">
                        현재 트래픽과 메트릭을 기반으로 이벤트 운영 정책을 자동으로 제안합니다.
                    </p>
                </div>
            </div>

            {/* 분석 전 상태 */}
            {!aiData && (
                <div className="card">
                    <div className="card-body text-center py-5">
                        <h2 className="h5 mb-3">현재 트래픽 상황에 대한 최적의 정책을 추천받아보세요</h2>
                        <p className="text-muted small mb-4">
                            AI가 최근 메트릭과 로그를 기반으로 입장 허용량·동시 접속·차단 룰을 제안합니다.
                        </p>
                        <button
                            type="button"
                            className="btn btn-primary btn-lg"
                            onClick={handleRequestAi}
                            disabled={loading}
                        >
                            {loading ? 'AI가 시스템 지표를 분석 중입니다...' : '실시간 AI 트래픽 분석 시작'}
                        </button>
                    </div>
                </div>
            )}

            {/* 분석 결과 화면 */}
            {aiData && (
                <div className="row g-4">
                    <div className="col-lg-5">
                        <div className="card h-100">
                            <div className="card-body d-flex flex-column justify-content-between">
                                <div>
                                    <h2 className="h5 fw-semibold mb-3">
                                        분석 결과 요약 (Action: {aiData.actionType})
                                    </h2>
                                    <p className="text-muted small mb-3">
                                        AI가 현재 메트릭을 바탕으로 아래와 같이 판단했습니다.
                                    </p>
                                    <div className="p-3 bg-light rounded mb-4 small">
                                        {aiData.rationale}
                                    </div>
                                </div>

                                <button
                                    type="button"
                                    className="btn btn-success w-100 py-3 fw-bold"
                                    onClick={handleApply}
                                    disabled={applying}
                                >
                                    {applying ? '적용 중...' : '추천 정책 즉시 적용'}
                                </button>
                            </div>
                        </div>
                    </div>

                    <div className="col-lg-7">
                        <div className="card h-100">
                            <div className="card-body">
                                <h2 className="h5 fw-semibold mb-3">추천 정책 상세</h2>
                                <div className="row g-3 mb-3">
                                    <div className="col-md-6">
                                        <div className="p-3 bg-light rounded border-start border-primary border-4">
                                            <p className="text-muted small mb-1">권장 입장 허용량 (admissionRps)</p>
                                            <p className="fw-bold mb-0 fs-4 text-primary">
                                                {aiData.recommendedAdmissionRps} RPS
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                <div className="mt-4">
                                    <p className="text-muted small mb-2">권장 차단 룰 (Block Rules)</p>
                                    <div className="d-flex flex-wrap gap-2">
                                        {(() => {
                                            const rules = aiData.suggestedBlockRules || {}
                                            const ipRanges = rules.ipRanges || []
                                            const userAgentPatterns = rules.userAgentPatterns ? [rules.userAgentPatterns] : []
                                            const allRules = [
                                                ...ipRanges.map((r) => ({ rule: r, type: 'IP' })),
                                                ...userAgentPatterns.map((r) => ({ rule: r, type: 'User-Agent' })),
                                            ]
                                            if (allRules.length === 0) {
                                                return (
                                                    <span className="text-muted small">
                            추가로 권장되는 차단 룰이 없습니다.
                          </span>
                                                )
                                            }
                                            return allRules.map(({ rule, type }, idx) => (
                                                <span key={`${type}-${idx}`} className="badge bg-danger px-3 py-2">
                          {rule}
                        </span>
                                            ))
                                        })()}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}

