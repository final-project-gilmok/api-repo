import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/admin/events', label: 'Events', icon: 'list' },
  { to: '/admin/settings', label: 'Settings', icon: 'gear' },
]

/**
 * Render the admin layout containing a sidebar navigation and a main content area.
 *
 * The layout includes a NavLink-based vertical sidebar with icons and a profile link, a header
 * with brand and profile indicator, a main content region that renders nested routes via <Outlet />,
 * and a footer.
 *
 * @returns {JSX.Element} The root JSX element for the admin layout.
 */
export default function Layout() {
  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <div className="px-3 mb-3">
          <small className="text-muted text-uppercase fw-semibold">Menu</small>
        </div>
        <nav className="nav flex-column">
          {navItems.map(({ to, label, icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive: active }) => `nav-link ${active ? 'active' : ''}`}
            >
              {icon === 'list' && (
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/>
                </svg>
              )}
              {icon === 'gear' && (
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                  <path d="M19.14 12.94c.04-.31.06-.63.06-.94 0-.31-.02-.63-.06-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
                </svg>
              )}
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto px-3 pt-3 border-top">
          <NavLink to="/admin/profile" className="nav-link">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M19.14 12.94c.04-.31.06-.63.06-.94 0-.31-.02-.63-.06-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
            </svg>
            Admin Profile
          </NavLink>
        </div>
        <div className="px-3 mt-2">
          <small className="text-muted">Made with V</small>
        </div>
      </aside>
      <div className="admin-main">
        <header className="admin-header">
          <a href="/admin/events" className="d-flex align-items-center gap-2 text-decoration-none text-dark fw-semibold">
            <img src="/logo.png" alt="gilmok" width={28} height={28} />
            gilmok
          </a>
          <div className="d-flex align-items-center">
            <div className="rounded-circle bg-secondary" style={{ width: 36, height: 36 }} title="Profile" />
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