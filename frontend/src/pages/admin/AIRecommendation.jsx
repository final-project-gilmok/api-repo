import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import { getPolicy } from '../../api/policy'

const base = (eventId) => (eventId ? `/admin/events/${eventId}` : '/admin')

const DEFAULT_SPEC = {
    cpuCores: '',
    memoryGb: '',
    instanceType: '',
    replicaCount: '',
}

const SPEC_STORAGE_KEY = 'ai_recommendation_server_spec'

const loadSavedSpec = () => {
    try {
        const saved = localStorage.getItem(SPEC_STORAGE_KEY)
        return saved ? JSON.parse(saved) : DEFAULT_SPEC
    } catch {
        return DEFAULT_SPEC
    }
}

const hasAnySpec = (spec) =>
    spec.cpuCores || spec.memoryGb || spec.instanceType || spec.replicaCount

export default function AIRecommendation() {
    const { eventId } = useParams()
    const navigate = useNavigate()

    const [loading, setLoading] = useState(false)
    const [applying, setApplying] = useState(false)
    const [aiData, setAiData] = useState(null)
    const [serverSpec, setServerSpec] = useState(loadSavedSpec) // ✅ 이전 입력값 복원
    const [specError, setSpecError] = useState('')

    const handleSpecChange = (e) => {
        const { name, value } = e.target
        setServerSpec(prev => {
            const updated = { ...prev, [name]: value }
            localStorage.setItem(SPEC_STORAGE_KEY, JSON.stringify(updated)) // ✅ 자동 저장
            return updated
        })
    }

    const validateSpec = () => {
        const { cpuCores, memoryGb, instanceType, replicaCount } = serverSpec
        const filledCount = [cpuCores, memoryGb, instanceType, replicaCount].filter(Boolean).length
        if (filledCount > 0 && filledCount < 4) {
            setSpecError('서버 스펙은 전부 입력하거나, 전부 비워주세요.')
            return false
        }
        setSpecError('')
        return true
    }

    const handleRequestAi = async () => {
        if (!eventId) return
        if (!validateSpec()) return

        setLoading(true)
        try {
            const body = hasAnySpec(serverSpec)
                ? {
                    cpuCores: Number(serverSpec.cpuCores),
                    memoryGb: Number(serverSpec.memoryGb),
                    instanceType: serverSpec.instanceType,
                    replicaCount: Number(serverSpec.replicaCount),
                }
                : {}

            const data = await api.post(`/admin/events/${eventId}/recommendation`, body)
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
                        ipPattern: Array.isArray(suggested.ipRanges) && suggested.ipRanges.length > 0
                            ? suggested.ipRanges.join(',') : null,
                        userAgentPattern: suggested.userAgentPatterns || null,
                        rateLimitKey: null,
                    }
                    : null

            let admissionConcurrency = aiData.recommendedAdmissionConcurrency
            if (admissionConcurrency === undefined || admissionConcurrency === null) {
                try {
                    const currentPolicy = await getPolicy(eventId)
                    admissionConcurrency = currentPolicy?.admissionConcurrency ?? 50
                } catch {
                    admissionConcurrency = 50
                }
            }

            await api.put(`/admin/events/${eventId}/policy`, {
                admissionRps: aiData.recommendedAdmissionRps,
                admissionConcurrency,
                blockRules,
            })
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
            <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <div>
                    <h1 className="h3 fw-bold mb-1">AI 정책 추천</h1>
                    <p className="text-muted small mb-0">
                        서버 스펙과 실시간 메트릭을 조합하여 최적의 운영 정책을 제안합니다.
                    </p>
                </div>
            </div>

            {/* 서버 스펙 입력 폼 */}
            {!aiData && (
                <div className="card">
                    <div className="card-body">
                        <h2 className="h5 fw-semibold mb-1">
                            서버 스펙 입력
                            <span className="text-muted fw-normal fs-6 ms-2">(선택)</span>
                        </h2>
                        <p className="text-muted small mb-3">
                            입력하면 스펙에 맞는 정책을 추천받을 수 있습니다.
                            입력하지 않으면 실시간 메트릭만으로 분석합니다.
                            {hasAnySpec(serverSpec) && (
                                <span className="text-success ms-1">✓ 이전에 입력한 스펙이 복원되었습니다.</span>
                            )}
                        </p>

                        <div className="row g-3 mb-3">
                            <div className="col-md-3">
                                <label className="form-label small fw-semibold">인스턴스 타입</label>
                                <input
                                    type="text"
                                    className="form-control"
                                    name="instanceType"
                                    placeholder="예: t3.medium"
                                    value={serverSpec.instanceType}
                                    onChange={handleSpecChange}
                                />
                            </div>
                            <div className="col-md-3">
                                <label className="form-label small fw-semibold">CPU 코어 수</label>
                                <input
                                    type="number"
                                    className="form-control"
                                    name="cpuCores"
                                    placeholder="예: 4"
                                    min="1"
                                    value={serverSpec.cpuCores}
                                    onChange={handleSpecChange}
                                />
                            </div>
                            <div className="col-md-3">
                                <label className="form-label small fw-semibold">메모리 (GB)</label>
                                <input
                                    type="number"
                                    className="form-control"
                                    name="memoryGb"
                                    placeholder="예: 16"
                                    min="1"
                                    value={serverSpec.memoryGb}
                                    onChange={handleSpecChange}
                                />
                            </div>
                            <div className="col-md-3">
                                <label className="form-label small fw-semibold">레플리카 수</label>
                                <input
                                    type="number"
                                    className="form-control"
                                    name="replicaCount"
                                    placeholder="예: 3"
                                    min="1"
                                    value={serverSpec.replicaCount}
                                    onChange={handleSpecChange}
                                />
                            </div>
                        </div>

                        {specError && (
                            <div className="alert alert-danger py-2 small">{specError}</div>
                        )}

                        <div className="text-center mt-3">
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
                </div>
            )}

            {/* 분석 결과 화면 */}
            {aiData && (
                <>
                    {/* 입력 스펙 요약 */}
                    {hasAnySpec(serverSpec) && (
                        <div className="card border-secondary">
                            <div className="card-body py-2">
                                <p className="text-muted small mb-0">
                                    📊 분석 기준 스펙: <strong>{serverSpec.instanceType}</strong> |
                                    CPU <strong>{serverSpec.cpuCores}코어</strong> |
                                    메모리 <strong>{serverSpec.memoryGb}GB</strong> |
                                    레플리카 <strong>{serverSpec.replicaCount}대</strong>
                                    <button
                                        className="btn btn-link btn-sm py-0 ms-2"
                                        onClick={() => setAiData(null)}
                                    >
                                        다시 분석
                                    </button>
                                </p>
                            </div>
                        </div>
                    )}

                    <div className="row g-4">
                        <div className="col-lg-5">
                            <div className="card h-100">
                                <div className="card-body d-flex flex-column justify-content-between">
                                    <div>
                                        <h2 className="h5 fw-semibold mb-3">
                                            분석 결과 요약 (Action: {aiData.actionType})
                                        </h2>
                                        <p className="text-muted small mb-3">
                                            AI가 현재 메트릭과 서버 스펙을 바탕으로 아래와 같이 판단했습니다.
                                        </p>
                                        <div className="p-3 bg-light rounded mb-4 small">
                                            {aiData.rationale}
                                        </div>

                                        {/* ✅ 추천 서버 스펙 */}
                                        {aiData.recommendedServerSpec && (
                                            <div className="mt-2">
                                                <p className="text-muted small mb-2">추천 서버 스펙</p>
                                                <div className={`p-3 rounded border-start border-4 bg-light
                                                    ${aiData.recommendedServerSpec.scaleAction === 'SCALE_UP' ? 'border-warning' :
                                                    aiData.recommendedServerSpec.scaleAction === 'SCALE_DOWN' ? 'border-info' : 'border-secondary'}`}>
                                                    <div className="d-flex gap-3 flex-wrap mb-2">
                                                        <span className="small">CPU <strong>{aiData.recommendedServerSpec.cpuCores}코어</strong></span>
                                                        <span className="small">메모리 <strong>{aiData.recommendedServerSpec.memoryGb}GB</strong></span>
                                                        <span className="small">레플리카 <strong>{aiData.recommendedServerSpec.replicaCount}대</strong></span>
                                                        <span className={`badge ${
                                                            aiData.recommendedServerSpec.scaleAction === 'SCALE_UP' ? 'bg-warning text-dark' :
                                                                aiData.recommendedServerSpec.scaleAction === 'SCALE_DOWN' ? 'bg-info text-dark' : 'bg-secondary'
                                                        }`}>
                                                            {aiData.recommendedServerSpec.scaleAction}
                                                        </span>
                                                    </div>
                                                    <p className="small text-muted mb-0">{aiData.recommendedServerSpec.reason}</p>
                                                </div>
                                            </div>
                                        )}
                                    </div>

                                    <button
                                        type="button"
                                        className="btn btn-success w-100 py-3 fw-bold mt-3"
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
                                        <div className="col-md-6">
                                            <div className="p-3 bg-light rounded border-start border-success border-4">
                                                <p className="text-muted small mb-1">권장 동시 접속 수</p>
                                                <p className="fw-bold mb-0 fs-4 text-success">
                                                    {aiData.recommendedAdmissionConcurrency != null
                                                        ? aiData.recommendedAdmissionConcurrency
                                                        : '기존 값 유지'}
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
                                                    return <span className="text-muted small">추가로 권장되는 차단 룰이 없습니다.</span>
                                                }
                                                return allRules.map(({ rule, type }, idx) => (
                                                    <span key={`${type}-${idx}`} className="badge bg-danger px-3 py-2">{rule}</span>
                                                ))
                                            })()}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </div>
    )
}
