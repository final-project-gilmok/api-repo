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

// ✅ 이슈1: eventId별로 키 분리
const getSpecStorageKey = (eventId) => `ai_recommendation_server_spec_${eventId}`

const loadSavedSpec = (eventId) => {
    try {
        const saved = localStorage.getItem(getSpecStorageKey(eventId))
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
    const [serverSpec, setServerSpec] = useState(() => loadSavedSpec(eventId)) // ✅ 이슈1
    const [specError, setSpecError] = useState('')

    const handleSpecChange = (e) => {
        const { name, value } = e.target
        setServerSpec(prev => {
            const updated = { ...prev, [name]: value }
            localStorage.setItem(getSpecStorageKey(eventId), JSON.stringify(updated)) // ✅ 이슈1
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
            {/* 페이지 헤더 */}
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
                        <div className="d-flex align-items-center gap-2 mb-1">
                            <h2 className="h5 fw-semibold mb-0">서버 스펙 입력</h2>
                            <span className="badge bg-secondary fw-normal">선택 사항</span>
                        </div>

                        <div className="alert alert-info py-2 small mb-3">
                            🔍 <strong>서버 스펙을 입력하면 더 정확한 정책을 추천받을 수 있습니다.</strong>
                            <br />
                            CPU, 메모리, 인스턴스 수를 기반으로 AI가 서버 처리 용량을 계산하여
                            RPS·동시 접속 수를 최적화된 값으로 제안합니다.
                            입력하지 않으면 실시간 메트릭만으로 분석합니다.
                            {hasAnySpec(serverSpec) && (
                                <span className="text-success d-block mt-1">✓ 이전에 입력한 스펙이 복원되었습니다.</span>
                            )}
                        </div>

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
                                <div className="form-text">AWS/OCI 인스턴스 타입 또는 서버 사양 이름</div>
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
                                <div className="form-text">서버 1대 기준 할당된 CPU 코어 수</div>
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
                                <div className="form-text">서버 1대 기준 할당된 메모리 용량</div>
                            </div>
                            <div className="col-md-3">
                                <label className="form-label small fw-semibold">인스턴스 수 (레플리카 수)</label>
                                <input
                                    type="number"
                                    className="form-control"
                                    name="replicaCount"
                                    placeholder="예: 3"
                                    min="1"
                                    value={serverSpec.replicaCount}
                                    onChange={handleSpecChange}
                                />
                                <div className="form-text">현재 실행 중인 서버(컨테이너) 대수</div>
                            </div>
                        </div>

                        {specError && (
                            <div className="alert alert-danger py-2 small">{specError}</div>
                        )}

                        {/* 총 처리 용량 미리보기 */}
                        {hasAnySpec(serverSpec) &&
                            serverSpec.cpuCores && serverSpec.memoryGb && serverSpec.replicaCount && (
                                <div className="alert alert-secondary py-2 small mb-3">
                                    📐 총 처리 용량 미리보기:
                                    CPU <strong>{serverSpec.cpuCores * serverSpec.replicaCount}코어</strong> |
                                    메모리 <strong>{serverSpec.memoryGb * serverSpec.replicaCount}GB</strong>
                                    ({serverSpec.replicaCount}대 기준)
                                </div>
                            )}

                        <div className="text-center mt-3">
                            <button
                                type="button"
                                className="btn btn-primary btn-lg"
                                onClick={handleRequestAi}
                                disabled={loading}
                            >
                                {loading ? (
                                    <>
                                        <span className="spinner-border spinner-border-sm me-2" />
                                        AI가 시스템 지표를 분석 중입니다...
                                    </>
                                ) : '실시간 AI 트래픽 분석 시작'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 분석 결과 화면 */}
            {aiData && (
                <>
                    {/* 입력 스펙 요약 - 스펙 있을 때만 */}
                    {hasAnySpec(serverSpec) && (
                        <div className="card border-secondary">
                            <div className="card-body py-2">
                                <p className="text-muted small mb-0">
                                    📊 분석 기준 스펙: <strong>{serverSpec.instanceType}</strong> |
                                    CPU <strong>{serverSpec.cpuCores}코어</strong> |
                                    메모리 <strong>{serverSpec.memoryGb}GB</strong> |
                                    인스턴스 <strong>{serverSpec.replicaCount}대</strong>
                                </p>
                            </div>
                        </div>
                    )}

                    {/* ✅ 이슈2: 다시 분석 버튼 항상 노출 */}
                    <div className="text-end">
                        <button
                            className="btn btn-outline-secondary btn-sm"
                            onClick={() => setAiData(null)}
                        >
                            🔄 다시 분석
                        </button>
                    </div>

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

                                        {/* 추천 서버 스펙 */}
                                        {aiData.recommendedServerSpec && (
                                            <div className="mt-2">
                                                <p className="text-muted small mb-2">추천 서버 스펙</p>
                                                <div className={`p-3 rounded border-start border-4 bg-light
                                                    ${aiData.recommendedServerSpec.scaleAction === 'SCALE_UP' ? 'border-warning' :
                                                    aiData.recommendedServerSpec.scaleAction === 'SCALE_DOWN' ? 'border-info' : 'border-secondary'}`}>
                                                    <div className="d-flex gap-3 flex-wrap mb-2">
                                                        <span className="small">CPU <strong>{aiData.recommendedServerSpec.cpuCores}코어</strong></span>
                                                        <span className="small">메모리 <strong>{aiData.recommendedServerSpec.memoryGb}GB</strong></span>
                                                        <span className="small">인스턴스 <strong>{aiData.recommendedServerSpec.replicaCount}대</strong></span>
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
                                        {applying ? (
                                            <>
                                                <span className="spinner-border spinner-border-sm me-2" />
                                                적용 중...
                                            </>
                                        ) : '추천 정책 즉시 적용'}
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
