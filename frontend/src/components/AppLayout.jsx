import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNotificationsSocket } from '../context/useNotificationsSocket';
import { api } from '../api/client';

/**
 * Shared shell (sidebar + top-level nav) for every authenticated page.
 * Also owns the live "unread notifications" badge, fed by the personal
 * notifications WebSocket topic so it updates without polling.
 */
export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!user) return;
    api.get('/notifications/unread-count').then((res) => setUnreadCount(res.count)).catch(() => {});
  }, [user]);

  useNotificationsSocket(user?.id, () => {
    setUnreadCount((count) => count + 1);
  });

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">Collab Platform</div>
        <nav className="sidebar-nav">
          <NavLink to="/projects" className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
            Projects
          </NavLink>
          <NavLink to="/notifications" className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
            Notifications
            {unreadCount > 0 && <span className="sidebar-badge">{unreadCount}</span>}
          </NavLink>
          <NavLink to="/profile" className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}>
            Profile
          </NavLink>
        </nav>
        <div className="sidebar-footer">
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 2 }}>{user?.name}</div>
          <div style={{ fontSize: 12, color: 'var(--color-text-muted)', marginBottom: 10 }}>{user?.email}</div>
          <button className="btn btn-secondary btn-block" onClick={handleLogout}>Log out</button>
        </div>
      </aside>
      <main className="main-content">
        <Outlet context={{ unreadCount, setUnreadCount }} />
      </main>
    </div>
  );
}
