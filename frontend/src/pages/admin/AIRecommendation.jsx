import React, { useState } from 'react';
import { useParams, NavLink, useNavigate } from 'react-router-dom';
import { api } from '../../api/client'; // client.js 경로 확인

const base = (eventId) => eventId ? `/admin/events/${eventId}` : '/admin';
const tabs = (eventId) => [
  { to: `${base(eventId)}/policy`, label: '정책 설정' },
  { to: `${base(eventId)}/monitoring`, label: '모니터링 요약' },
  { to: `${base(eventId)}/ai-recommendation`, label: 'AI 추천' },
];

export default function AIRecommendation() {
  const { eventId } = useParams();
  const navigate = useNavigate();

  const showTabs = !!eventId;
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [aiData, setAiData] = useState(null);

  const handleRequestAi = async () => {
    setLoading(true);
    try {
      // replace(':', '') 같은 꼼수 제거하고 정석대로 호출 (App.jsx 라우터가 정상이라면 eventId에는 숫자만 들어옴)
      // 백엔드가 POST로 변경되었으므로 api.post 사용
      const data = await api.post(`/api/admin/events/${eventId}/recommendation`, {});
      setAiData(data);
    } catch (error) {
      console.error('AI 분석 실패:', error);
      alert('AI 분석 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 2. 추천 정책 즉시 적용 함수 (✅ 리뷰 반영: 빈 배열([]) 대신 null 전달)
  const handleApply = async () => {
    if (!aiData) return;
    if (!window.confirm('AI가 추천한 이 정책을 실제 서버에 즉시 적용하시겠습니까?')) return;

    setApplying(true);
    try {
      const updateRequest = {
        admissionRps: aiData.recommendedAdmissionRps,
        tokenTtlSeconds: aiData.recommendedTokenTtlSeconds,
        // BlockRules 객체를 기대하는 백엔드를 위해 [] 대신 null 전달
        blockRules: aiData.suggestedBlockRules || null
      };

      await api.put(`/api/admin/events/${eventId}/policy`, updateRequest);
      alert('🚀 추천 정책이 성공적으로 적용되었습니다!');
      navigate(`${base(eventId)}/policy`);
    } catch (error) {
      console.error('정책 적용 실패:', error);
      alert('정책 적용에 실패했습니다.');
    } finally {
      setApplying(false);
    }
  };

  return (
      <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
        <h1 className="h3 mb-4 fw-bold">🤖 AI 정책 추천</h1>

        {showTabs && (
            <ul className="nav event-detail-tabs nav-tabs mb-4">
              {tabs(eventId).map((tab) => (
                  <li key={tab.to} className="nav-item">
                    <NavLink className="nav-link" to={tab.to}>{tab.label}</NavLink>
                  </li>
              ))}
            </ul>
        )}

        {/* AI 데이터가 없을 때 (분석 전) */}
        {!aiData && (
            <div className="text-center py-5 border rounded bg-light">
              <h4 className="mb-3">현재 트래픽 상황에 대한 최적의 정책을 추천받아보세요</h4>
              <button
                  className="btn btn-primary btn-lg"
                  onClick={handleRequestAi}
                  disabled={loading}
              >
                {loading ? '⏳ AI가 시스템 지표를 분석 중입니다 (3~5초 소요)...' : '✨ 실시간 AI 트래픽 분석 시작'}
              </button>
            </div>
        )}

        {/* AI 데이터가 있을 때 (분석 완료 후 결과 화면) */}
        {aiData && (
            <div className="row g-4">
              <div className="col-lg-5">
                <div className="card border rounded-3 h-100">
                  <div className="card-body d-flex flex-column justify-content-between">
                    <div>
                      <h2 className="h5 fw-semibold mb-3">분석 결과 요약 (Action: {aiData.actionType})</h2>
                      <p className="text-muted small mb-3">AI가 현재 메트릭을 바탕으로 아래와 같이 판단했습니다.</p>
                      <div className="p-3 bg-light rounded mb-4" style={{ fontSize: '0.95rem', lineHeight: '1.6' }}>
                        {aiData.rationale}
                      </div>
                    </div>

                    <button
                        type="button"
                        className="btn btn-success w-100 py-3 fw-bold"
                        onClick={handleApply}
                        disabled={applying}
                    >
                      {applying ? '적용 중...' : '🚀 추천 정책 즉시 적용 (Apply)'}
                    </button>
                  </div>
                </div>
              </div>

              <div className="col-lg-7">
                <div className="card border rounded-3">
                  <div className="card-body">
                    <h2 className="h5 fw-semibold mb-3">추천 정책 상세 (Recommendation Details)</h2>
                    <div className="row g-3 mb-3">
                      <div className="col-md-6">
                        <div className="p-3 bg-light rounded border-start border-primary border-4">
                          <p className="text-muted small mb-1">권장 입장 허용량 (admissionRps)</p>
                          <p className="fw-bold mb-0 fs-4 text-primary">{aiData.recommendedAdmissionRps} RPS</p>
                        </div>
                      </div>
                      <div className="col-md-6">
                        <div className="p-3 bg-light rounded border-start border-success border-4">
                          <p className="text-muted small mb-1">권장 토큰 만료시간 (tokenTtl)</p>
                          <p className="fw-bold mb-0 fs-4 text-success">{aiData.recommendedTokenTtlSeconds} 초</p>
                        </div>
                      </div>
                    </div>

                    <div className="mb-3 mt-4">
                      <p className="text-muted small mb-2">권장 차단 룰 (Block Rules)</p>
                      <div className="d-flex flex-wrap gap-2">
                        {aiData.suggestedBlockRules?.ipRanges && aiData.suggestedBlockRules.ipRanges.length > 0 ? (
                            aiData.suggestedBlockRules.ipRanges.map((rule, idx) => (
                                <span key={idx} className="badge bg-danger px-3 py-2">{rule}</span>
                            ))
                        ) : (
                            <span className="text-muted small">추가로 권장되는 차단 룰이 없습니다.</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
        )}
      </div>
  );
}
