import { Outlet, Link, NavLink } from 'react-router-dom'

export default function UserLayout() {
  return (
    <div className="user-layout">
      <nav className="navbar navbar-expand-lg navbar-light bg-white border-bottom">
        <div className="container">
          <Link to="/" className="navbar-brand fw-bold d-flex align-items-center gap-2">
            <img src="/logo.png" alt="gilmok" width={28} height={28} />
            gilmok
          </Link>
          <div className="d-flex align-items-center gap-3">
            <NavLink to="/my-reservations" className="btn btn-outline-primary btn-sm">
              내 예약
            </NavLink>
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
