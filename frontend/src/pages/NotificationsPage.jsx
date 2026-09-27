import { useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { api } from '../api/client';

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState(null);
  const [error, setError] = useState('');
  const outletContext = useOutletContext();

  useEffect(() => {
    api.get('/notifications').then(setNotifications).catch((err) => setError(err.message));
  }, []);

  async function handleMarkRead(id) {
    try {
      await api.patch(`/notifications/${id}/read`);
      setNotifications((current) => current.map((n) => (n.id === id ? { ...n, read: true } : n)));
      outletContext?.setUnreadCount?.((count) => Math.max(0, count - 1));
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Notifications</h1>
          <p className="page-subtitle">Task assignments, status changes, and comments that involve you.</p>
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {notifications && notifications.length === 0 && (
        <div className="empty-state">You're all caught up.</div>
      )}

      {notifications && notifications.map((n) => (
        <div key={n.id} className={`notification-item${n.read ? '' : ' unread'}`}>
          <div style={{ flex: 1 }}>
            <div>{n.message}</div>
            <div style={{ fontSize: 12, color: 'var(--color-text-muted)', marginTop: 4 }}>
              {new Date(n.createdAt).toLocaleString()}
            </div>
          </div>
          {!n.read && (
            <button className="btn btn-secondary" onClick={() => handleMarkRead(n.id)}>Mark read</button>
          )}
        </div>
      ))}
    </div>
  );
}
