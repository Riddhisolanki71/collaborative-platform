import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { api } from '../api/client';
import { useProjectSocket } from '../context/useProjectSocket';

const STATUSES = ['TODO', 'IN_PROGRESS', 'DONE'];
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export default function TaskDetailsPage() {
  const { taskId } = useParams();
  const navigate = useNavigate();
  const [task, setTask] = useState(null);
  const [members, setMembers] = useState([]);
  const [comments, setComments] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [error, setError] = useState('');
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editPriority, setEditPriority] = useState('MEDIUM');
  const [editDueDate, setEditDueDate] = useState('');

  const loadTask = useCallback(async () => {
    try {
      const t = await api.get(`/tasks/${taskId}`);
      setTask(t);
      setEditTitle(t.title);
      setEditDescription(t.description || '');
      setEditPriority(t.priority);
      setEditDueDate(t.dueDate || '');
      const [m, c] = await Promise.all([
        api.get(`/projects/${t.projectId}/members`),
        api.get(`/tasks/${taskId}/comments`),
      ]);
      setMembers(m);
      setComments(c);
    } catch (err) {
      setError(err.message);
    }
  }, [taskId]);

  useEffect(() => { loadTask(); }, [loadTask]);

  // Live-update this task if someone else changes it or comments on it while we're viewing it.
  useProjectSocket(task?.projectId, (event) => {
    if (['TASK_UPDATED', 'TASK_STATUS_CHANGED', 'TASK_ASSIGNED'].includes(event.type) && event.payload.id === Number(taskId)) {
      setTask(event.payload);
    }
    if (event.type === 'COMMENT_ADDED' && event.payload.taskId === Number(taskId)) {
      setComments((current) => [...current, event.payload]);
    }
    if (event.type === 'TASK_DELETED' && event.payload === Number(taskId)) {
      navigate(-1);
    }
  });

  async function handleStatusChange(newStatus) {
    setError('');
    try {
      const updated = await api.patch(`/tasks/${taskId}/status`, { status: newStatus, version: task.version });
      setTask(updated);
    } catch (err) {
      setError(err.status === 409
        ? 'This task changed elsewhere - reloading the latest version.'
        : err.message);
      loadTask();
    }
  }

  async function handleAssign(assigneeId) {
    setError('');
    try {
      const updated = await api.patch(`/tasks/${taskId}/assign`, { assigneeId: assigneeId ? Number(assigneeId) : null });
      setTask(updated);
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleSaveDetails(e) {
    e.preventDefault();
    setError('');
    try {
      const updated = await api.put(`/tasks/${taskId}`, {
        title: editTitle,
        description: editDescription,
        priority: editPriority,
        dueDate: editDueDate || null,
        version: task.version,
      });
      setTask(updated);
    } catch (err) {
      setError(err.status === 409
        ? 'This task changed elsewhere - reloading the latest version.'
        : (err.details?.join(' ') || err.message));
      loadTask();
    }
  }

  async function handleAddComment(e) {
    e.preventDefault();
    if (!newComment.trim()) return;
    try {
      const comment = await api.post(`/tasks/${taskId}/comments`, { content: newComment });
      setComments((current) => [...current, comment]);
      setNewComment('');
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDelete() {
    if (!confirm('Delete this task? This cannot be undone.')) return;
    try {
      await api.del(`/tasks/${taskId}`);
      navigate(`/projects/${task.projectId}/board`);
    } catch (err) {
      setError(err.message);
    }
  }

  if (error && !task) return <div className="error-banner">{error}</div>;
  if (!task) return <div className="empty-state">Loading...</div>;

  return (
    <div>
      <div className="page-header">
        <div>
          <Link to={`/projects/${task.projectId}/board`} className="page-subtitle">&larr; Back to board</Link>
          <h1 style={{ marginTop: 6 }}>{task.title}</h1>
        </div>
        <button className="btn btn-danger" onClick={handleDelete}>Delete task</button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="task-detail-layout">
        <div className="card task-detail-main">
          <form onSubmit={handleSaveDetails}>
            <div className="field">
              <label>Title</label>
              <input value={editTitle} onChange={(e) => setEditTitle(e.target.value)} required />
            </div>
            <div className="field">
              <label>Description</label>
              <textarea rows={5} value={editDescription} onChange={(e) => setEditDescription(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: 16 }}>
              <div className="field" style={{ flex: 1 }}>
                <label>Priority</label>
                <select value={editPriority} onChange={(e) => setEditPriority(e.target.value)}>
                  {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </div>
              <div className="field" style={{ flex: 1 }}>
                <label>Due date</label>
                <input type="date" value={editDueDate} onChange={(e) => setEditDueDate(e.target.value)} />
              </div>
            </div>
            <button className="btn btn-secondary" type="submit">Save changes</button>
          </form>

          <h2 style={{ fontSize: 15, marginTop: 28, marginBottom: 12 }}>Comments</h2>
          {comments.length === 0 && <div className="empty-state">No comments yet - be the first.</div>}
          {comments.map((comment) => (
            <div className="comment" key={comment.id}>
              <div className="comment-meta">
                <span className="comment-author">{comment.author.name}</span> &middot; {new Date(comment.createdAt).toLocaleString()}
              </div>
              <div>{comment.content}</div>
            </div>
          ))}

          <form onSubmit={handleAddComment} style={{ marginTop: 16, display: 'flex', gap: 8 }}>
            <input
              style={{ flex: 1, padding: '9px 11px', border: '1px solid var(--color-border)', borderRadius: 6 }}
              placeholder="Add a comment..."
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
            />
            <button className="btn btn-primary" type="submit">Post</button>
          </form>
        </div>

        <div className="card task-detail-side">
          <div className="side-field">
            <label>Status</label>
            <select value={task.status} onChange={(e) => handleStatusChange(e.target.value)}>
              {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div className="side-field">
            <label>Assignee</label>
            <select value={task.assignee?.id || ''} onChange={(e) => handleAssign(e.target.value)}>
              <option value="">Unassigned</option>
              {members.map((m) => (
                <option key={m.user.id} value={m.user.id}>{m.user.name}</option>
              ))}
            </select>
          </div>
          <div className="side-field">
            <label>Created by</label>
            <span>{task.creator.name}</span>
          </div>
          <div className="side-field">
            <label>Created</label>
            <span>{new Date(task.createdAt).toLocaleString()}</span>
          </div>
          <div className="side-field">
            <label>Last updated</label>
            <span>{new Date(task.updatedAt).toLocaleString()}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
