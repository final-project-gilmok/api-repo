import { useState } from 'react'
import { Link } from 'react-router-dom'

const MOCK_EVENTS = [
  { id: 'EVT001', name: '2024년 기술 컨퍼런스', status: 'open', createdAt: '2024-01-15' },
  { id: 'EVT002', name: '새로운 제품 런칭 데모', status: 'draft', createdAt: '2024-02-20' },
  { id: 'EVT003', name: '클라우드 보안 웨비나', status: 'closed', createdAt: '2023-11-10' },
  { id: 'EVT004', name: 'AI 혁신 포럼', status: 'open', createdAt: '2024-03-01' },
  { id: 'EVT005', name: '데이터 분석 입문 과정', status: 'draft', createdAt: '2024-04-05' },
]

const statusLabel = { open: '열림', draft: '초안', closed: '닫힘' }
const statusClass = { open: 'open', draft: 'draft', closed: 'closed' }

export default function EventManagement() {
  const [events, setEvents] = useState(MOCK_EVENTS)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const handleCreate = (e) => {
    e.preventDefault()
    const trimmedName = name.trim()
    const trimmedDescription = description.trim()
    if (!trimmedName) return
    setEvents((prev) => {
      const newId = `EVT${String(prev.length + 1).padStart(3, '0')}`
      return [
        ...prev,
        {
          id: newId,
          name: trimmedName,
          description: trimmedDescription,
          status: 'draft',
          createdAt: new Date().toISOString().slice(0, 10),
        },
      ]
    })
    setName('')
    setDescription('')
  }

  return (
    <>
      <h1 className="h3 mb-1 fw-bold">이벤트 관리</h1>
      <p className="text-muted mb-4">
        운영할 데모 이벤트를 생성하고 목록을 확인하세요. 각 이벤트의 상세 설정 페이지로 이동할 수 있습니다.
      </p>

      <div className="row g-4">
        <div className="col-lg-7">
          <div className="card border rounded-3">
            <div className="card-body">
              <h2 className="h5 fw-semibold mb-1">이벤트 목록</h2>
              <p className="text-muted small mb-3">현재 생성된 모든 이벤트를 확인하고 관리합니다.</p>
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0">
                  <thead>
                    <tr>
                      <th>이벤트 ID</th>
                      <th>이름</th>
                      <th>상태</th>
                      <th>생성일</th>
                      <th className="text-end" style={{ width: 48 }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {events.map((evt) => (
                      <tr key={evt.id}>
                        <td className="fw-medium">{evt.id}</td>
                        <td>{evt.name}</td>
                        <td>
                          <span className={`badge badge-status ${statusClass[evt.status]}`}>
                            {statusLabel[evt.status]}
                          </span>
                        </td>
                        <td>{evt.createdAt}</td>
                        <td className="text-end">
                          <Link
                            to={`/admin/events/${evt.id}`}
                            className="btn btn-sm btn-link text-secondary p-0"
                            title="상세"
                            aria-label="이벤트 상세 보기"
                          >
                            ⋮
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <div className="col-lg-5">
          <div className="card border rounded-3">
            <div className="card-body">
              <h2 className="h5 fw-semibold mb-1">새 이벤트 생성</h2>
              <p className="text-muted small mb-3">새로운 이벤트를 생성하고 데모 운영을 준비합니다.</p>
              <form onSubmit={handleCreate}>
                <div className="mb-3">
                  <label htmlFor="event-name" className="form-label">이벤트 이름 <span className="text-danger">*</span></label>
                  <input
                    id="event-name"
                    type="text"
                    className="form-control"
                    placeholder="예: 2026년 신년 맞이 프로모션"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                </div>
                <div className="mb-3">
                  <label htmlFor="event-description" className="form-label">이벤트 설명 (선택)</label>
                  <textarea
                    id="event-description"
                    className="form-control"
                    rows={4}
                    placeholder="이벤트에 대한 자세한 설명을 입력하세요."
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>
                <button type="submit" className="btn btn-primary">이벤트 생성</button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
