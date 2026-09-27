import { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';

export default function ProfilePage() {
  const { user } = useAuth();
  const [assigned, setAssigned] = useState([]);
  const [created, setCreated] = useState([]);
  const [overdue, setOverdue] = useState([]);

  useEffect(() => {
    api.get('/tasks/mine/assigned').then(setAssigned).catch(() => {});
    api.get('/tasks/mine/created').then(setCreated).catch(() => {});
    api.get('/tasks/mine/overdue').then(setOverdue).catch(() => {});
  }, []);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Your account</h1>
          <p className="page-subtitle">Tasks assigned to and created by you, across every project.</p>
        </div>
      </div>

      <div className="card" style={{ padding: 20, marginBottom: 24, maxWidth: 420 }}>
        <div style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
          <div className="avatar" style={{ width: 44, height: 44, fontSize: 15 }}>
            {user.name.slice(0, 2).toUpperCase()}
          </div>
          <div>
            <div style={{ fontWeight: 700 }}>{user.name}</div>
            <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>{user.email}</div>
          </div>
          <span className="role-tag" style={{ marginLeft: 'auto' }}>{user.role}</span>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 24 }}>
        <TaskColumn title="Assigned to you" tasks={assigned} />
        <TaskColumn title="Created by you" tasks={created} />
        <TaskColumn title="Overdue" tasks={overdue} />
      </div>
    </div>
  );
}

function TaskColumn({ title, tasks }) {
  return (
    <div className="dashboard-section">
      <h2>{title} ({tasks.length})</h2>
      {tasks.length === 0 && <div className="empty-state">Nothing here.</div>}
      {tasks.map((task) => (
        <div key={task.id} className="task-list-row">
          <span className={`priority-tag ${task.priority}`}>{task.priority}</span>
          <span className="title">{task.title}</span>
        </div>
      ))}
    </div>
  );
}
