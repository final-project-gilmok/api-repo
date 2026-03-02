import { useState, useEffect } from 'react'
import { useNavigate, useLocation, Link, Navigate } from 'react-router-dom'
import { authService } from '../../api/auth'
import Layout from '../../components/common/Layout'

export function AdminRouteGuard() {
  const location = useLocation()
  const [allowed, setAllowed] = useState(null)

  useEffect(() => {
    const accessToken = localStorage.getItem('accessToken')
    const role = (localStorage.getItem('role') || '').toString().toUpperCase()
    const isLoggedIn = !!accessToken
    const isAdmin = role.includes('ADMIN')
    setAllowed(isLoggedIn && isAdmin ? true : isLoggedIn ? 'no-role' : false)
  }, [location.pathname])

  if (allowed === null) {
    return (
      <div className="text-center py-5">
        <div className="spinner-border" role="status" />
      </div>
    )
  }
  if (allowed === false) {
    return <Navigate to="/auth/login" state={{ from: location }} replace />
  }
  if (allowed === 'no-role') {
    return <Navigate to="/" replace />
  }
  return <Layout />
}

export default function Login() {
    const navigate = useNavigate()
    const [formData, setFormData] = useState({
        username: '',
        password: '',
    })
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)

    const handleChange = (e) => {
        const { name, value } = e.target
        setFormData((prev) => ({ ...prev, [name]: value }))
    }

    const handleSubmit = async (e) => {
        e.preventDefault()
        setError('')
        setLoading(true)

        try {
            const data = await authService.login(formData)
            const result = data.data || data // Defensive: handle both wrapped and unwrapped

            // 필수 필드 검증 및 예외 처리
            if (!result.accessToken || !result.refreshToken || !result.username || !result.role) {
                throw new Error('로그인 응답 형식이 올바르지 않습니다. 다시 시도해 주세요.')
            }

            // Store tokens and user info
            localStorage.setItem('accessToken', result.accessToken)
            localStorage.setItem('refreshToken', result.refreshToken)
            localStorage.setItem('username', result.username)
            localStorage.setItem('role', result.role)
            if (result.userId != null) localStorage.setItem('userId', String(result.userId))

            const role = (result.role || '').toString().toUpperCase()
            if (role.includes('ADMIN')) {
                navigate('/admin')
            } else {
                navigate('/')
            }
        } catch (err) {
            setError(err.message || '로그인 중 오류가 발생했습니다.')
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="d-flex justify-content-center align-items-center" style={{ minHeight: '80vh' }}>
            <div className="card border shadow-sm" style={{ width: '100%', maxWidth: '400px' }}>
                <div className="card-body p-4">
                    <div className="text-center mb-4">
                        <img src="/logo.png" alt="gilmok" width={40} height={40} className="mb-2" />
                        <h1 className="h4 fw-bold">로그인</h1>
                        <p className="text-muted small">서비스를 이용하려면 로그인해 주세요.</p>
                    </div>

                    {error && (
                        <div className="alert alert-danger py-2 small" role="alert">
                            {error}
                        </div>
                    )}

                    <form onSubmit={handleSubmit}>
                        <div className="mb-3">
                            <label htmlFor="username" className="form-label small fw-semibold">아이디</label>
                            <input
                                type="text"
                                className="form-control"
                                id="username"
                                name="username"
                                value={formData.username}
                                onChange={handleChange}
                                placeholder="아이디를 입력하세요"
                                required
                            />
                        </div>

                        <div className="mb-4">
                            <label htmlFor="password" className="form-label small fw-semibold">비밀번호</label>
                            <input
                                type="password"
                                className="form-control"
                                id="password"
                                name="password"
                                value={formData.password}
                                onChange={handleChange}
                                placeholder="비밀번호를 입력하세요"
                                required
                            />
                        </div>

                        <button
                            type="submit"
                            className="btn btn-primary w-100 fw-semibold"
                            disabled={loading}
                        >
                            {loading ? (
                                <>
                                    <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                                    로그인 중...
                                </>
                            ) : (
                                '로그인'
                            )}
                        </button>
                    </form>

                    <div className="text-center mt-4 small">
                        <span className="text-muted">계정이 없으신가요? </span>
                        <Link to="/auth/signup" className="text-primary text-decoration-none fw-semibold">회원가입</Link>
                    </div>
                </div>
            </div>
        </div>
    )
}
