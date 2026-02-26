import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { authService } from '../../api/auth'

export default function Signup() {
    const navigate = useNavigate()
    const [formData, setFormData] = useState({
        username: '',
        password: '',
        passwordConfirm: '',
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

        // Client-side validation: Password match
        if (formData.password !== formData.passwordConfirm) {
            setError('비밀번호가 일치하지 않습니다.')
            return
        }

        setLoading(true)
        try {
            await authService.signup(formData)
            alert('회원가입이 완료되었습니다. 로그인해주세요.')
            navigate('/login') // Assuming a login page exists or will be created
        } catch (err) {
            setError(err.message || '회원가입 중 오류가 발생했습니다.')
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
                        <h1 className="h4 fw-bold">회원가입</h1>
                        <p className="text-muted small">gilmok에 오신 것을 환영합니다.</p>
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
                                placeholder="2~10자 사이로 입력해주세요"
                                required
                            />
                        </div>

                        <div className="mb-3">
                            <label htmlFor="password" className="form-label small fw-semibold">비밀번호</label>
                            <input
                                type="password"
                                className="form-control"
                                id="password"
                                name="password"
                                value={formData.password}
                                onChange={handleChange}
                                placeholder="8자 이상 입력해주세요"
                                required
                            />
                        </div>

                        <div className="mb-4">
                            <label htmlFor="passwordConfirm" className="form-label small fw-semibold">비밀번호 확인</label>
                            <input
                                type="password"
                                className="form-control"
                                id="passwordConfirm"
                                name="passwordConfirm"
                                value={formData.passwordConfirm}
                                onChange={handleChange}
                                placeholder="비밀번호를 다시 입력해주세요"
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
                                    가입 중...
                                </>
                            ) : (
                                '회원가입'
                            )}
                        </button>
                    </form>

                    <div className="text-center mt-4 small">
                        <span className="text-muted">이미 계정이 있으신가요? </span>
                        <Link to="/login" className="text-primary text-decoration-none fw-semibold">로그인</Link>
                    </div>
                </div>
            </div>
        </div>
    )
}
