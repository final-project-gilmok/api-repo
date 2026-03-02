import { Outlet, Link } from 'react-router-dom'

export default function Layout() {
  return (
    <div className="admin-layout">
      <div className="admin-main">
        <header className="admin-header">
          <Link to="/admin/events" className="d-flex align-items-center gap-2 text-decoration-none text-dark fw-semibold">
            <img src="/logo.png" alt="gilmok" width={28} height={28} />
            gilmok
          </Link>
          <div className="ms-auto d-flex align-items-center gap-3">
            <span className="fw-semibold text-muted">관리자 페이지</span>
            <Link to="/" className="btn btn-outline-secondary btn-sm">
              메인 페이지로 이동
            </Link>
          </div>
        </header>
        <main className="admin-content">
          <Outlet />
        </main>
        <footer className="admin-footer">
          <span>Made with V</span>
          <span>© 2026 gilmok. All rights reserved.</span>
        </footer>
      </div>
    </div>
  )
}
