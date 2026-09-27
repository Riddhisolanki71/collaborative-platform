import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useProjectSocket } from '../context/useProjectSocket';
import { useAuth } from '../context/AuthContext';
import ProjectTabs from '../components/ProjectTabs';

const ROLES = ['MEMBER', 'ADMIN'];

export default function ProjectMembersPage() {
  const { projectId } = useParams();
  const { user } = useAuth();
  const [project, setProject] = useState(null);
  const [members, setMembers] = useState([]);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('MEMBER');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const loadAll = useCallback(() => {
    Promise.all([
      api.get(`/projects/${projectId}`),
      api.get(`/projects/${projectId}/members`),
    ]).then(([p, m]) => { setProject(p); setMembers(m); }).catch((err) => setError(err.message));
  }, [projectId]);

  useEffect(() => { loadAll(); }, [loadAll]);

  useProjectSocket(projectId, (event) => {
    if (event.type === 'MEMBER_ADDED') {
      setMembers((current) => current.some((m) => m.id === event.payload.id) ? current : [...current, event.payload]);
    }
    if (event.type === 'MEMBER_REMOVED') {
      setMembers((current) => current.filter((m) => m.user.id !== event.payload));
    }
  });

  const canManage = project && (project.myRole === 'OWNER' || project.myRole === 'ADMIN');

  async function handleAdd(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const member = await api.post(`/projects/${projectId}/members`, { email, role });
      setMembers((current) => [...current, member]);
      setEmail('');
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRemove(memberUserId) {
    if (!confirm('Remove this member from the project?')) return;
    try {
      await api.del(`/projects/${projectId}/members/${memberUserId}`);
      setMembers((current) => current.filter((m) => m.user.id !== memberUserId));
    } catch (err) {
      setError(err.message);
    }
  }

  if (!project) return <div className="empty-state">Loading...</div>;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{project.name}</h1>
          <p className="page-subtitle">Manage who has access to this project.</p>
        </div>
      </div>

      <ProjectTabs projectId={projectId} />

      {error && <div className="error-banner">{error}</div>}

      {canManage && (
        <form onSubmit={handleAdd} className="card" style={{ padding: 20, marginBottom: 24, display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <div className="field" style={{ flex: 1, marginBottom: 0 }}>
            <label>Member email</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="teammate@example.com" required />
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label>Role</label>
            <select value={role} onChange={(e) => setRole(e.target.value)}>
              {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>
          <button className="btn btn-primary" type="submit" disabled={submitting}>Add member</button>
        </form>
      )}

      <div className="card" style={{ padding: '4px 20px' }}>
        {members.map((member) => (
          <div className="member-row" key={member.id}>
            <div className="avatar">{member.user.name.slice(0, 2).toUpperCase()}</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: 13 }}>{member.user.name}{member.user.id === user.id ? ' (you)' : ''}</div>
              <div style={{ fontSize: 12, color: 'var(--color-text-muted)' }}>{member.user.email}</div>
            </div>
            <span className="role-tag">{member.role}</span>
            {canManage && member.role !== 'OWNER' && (
              <button className="btn btn-secondary" onClick={() => handleRemove(member.user.id)}>Remove</button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
