import { Outlet, Link, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'

export default function UserLayout() {
    const navigate = useNavigate()
    const location = useLocation()
    const [auth, setAuth] = useState({
        isLoggedIn: false,
        username: '',
        role: '',
    })

    useEffect(() => {
        // 토큰 대신 'isLoggedIn' 플래그로 로그인 여부를 확인
        const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true'
        const username = localStorage.getItem('username') || ''
        const role = localStorage.getItem('role') || ''

        setAuth({
            isLoggedIn,
            username,
            role,
        })
    }, [location.pathname])

    const handleLogout = () => {
        // 모든 인증 관련 정보 삭제
        localStorage.removeItem('isLoggedIn')
        localStorage.removeItem('username')
        localStorage.removeItem('role')

        setAuth({
            isLoggedIn: false,
            username: '',
            role: '',
        })
        navigate('/')
    }

    return (
        <div className="user-layout">
            <nav className="navbar navbar-expand-lg navbar-light bg-white border-bottom">
                <div className="container">
                    <Link to="/" className="navbar-brand fw-bold d-flex align-items-center gap-2">
                        <img src="/logo.png" alt="gilmok" width={28} height={28} />
                        gilmok
                    </Link>
                    <div className="d-flex align-items-center gap-3">
                        {auth.isLoggedIn ? (
                            <>
                                <span className="small text-muted d-none d-md-inline">
                                    안녕하세요, <span className="fw-semibold">{auth.username || '사용자'}</span> 님
                                </span>
                                <NavLink to="/my" className="btn btn-outline-primary btn-sm">
                                    마이페이지
                                </NavLink>
                                {String(auth.role ?? '')
                                    .split(',')
                                    .map((r) => r.trim().toUpperCase())
                                    .some((r) => r.endsWith('ADMIN')) && (
                                        <NavLink to="/admin" className="btn btn-outline-secondary btn-sm">
                                            관리자 페이지
                                        </NavLink>
                                    )}
                                <button
                                    type="button"
                                    className="btn btn-link btn-sm text-decoration-none text-muted"
                                    onClick={handleLogout}
                                >
                                    로그아웃
                                </button>
                            </>
                        ) : (
                            <>
                                <NavLink to="/auth/login" className="btn btn-outline-secondary btn-sm">
                                    로그인
                                </NavLink>
                                <NavLink to="/auth/signup" className="btn btn-primary btn-sm">
                                    회원가입
                                </NavLink>
                            </>
                        )}
                    </div>
                </div>
            </nav>
            <main className="container py-4">
                <Outlet />
            </main>
            <footer className="bg-white border-top py-3 mt-auto">
                <div className="container d-flex justify-content-between text-muted small">
                    <span>gilmok</span>
                    <span>&copy; 2026 gilmok. All rights reserved.</span>
                </div>
            </footer>
        </div>
    )
}
