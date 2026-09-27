import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api/client';
import { useProjectSocket } from '../context/useProjectSocket';
import ProjectTabs from '../components/ProjectTabs';
import ActivityFeed from '../components/ActivityFeed';

export default function ProjectDashboardPage() {
  const { projectId } = useParams();
  const [project, setProject] = useState(null);
  const [dashboard, setDashboard] = useState(null);
  const [activities, setActivities] = useState([]);
  const [error, setError] = useState('');

  const loadAll = useCallback(() => {
    Promise.all([
      api.get(`/projects/${projectId}`),
      api.get(`/projects/${projectId}/dashboard`),
      api.get(`/projects/${projectId}/activities`),
    ])
      .then(([p, d, a]) => {
        setProject(p);
        setDashboard(d);
        setActivities(a);
      })
      .catch((err) => setError(err.message));
  }, [projectId]);

  useEffect(() => { loadAll(); }, [loadAll]);

  // Any task or activity change in the project should refresh the numbers -
  // dashboards are cheap enough here to just re-fetch rather than hand-patch
  // every counter for every event type.
  useProjectSocket(projectId, (event) => {
    if (['TASK_CREATED', 'TASK_UPDATED', 'TASK_STATUS_CHANGED', 'TASK_ASSIGNED', 'TASK_DELETED'].includes(event.type)) {
      api.get(`/projects/${projectId}/dashboard`).then(setDashboard).catch(() => {});
    }
    if (event.type === 'ACTIVITY_CREATED') {
      setActivities((current) => [event.payload, ...current]);
    }
  });

  if (error) return <div className="error-banner">{error}</div>;
  if (!project || !dashboard) return <div className="empty-state">Loading...</div>;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{project.name}</h1>
          <p className="page-subtitle">{project.description || 'No description yet.'}</p>
        </div>
        <Link to={`/projects/${projectId}/board`} className="btn btn-primary">Open board</Link>
      </div>

      <ProjectTabs projectId={projectId} />

      <div className="stat-grid">
        <div className="card stat-hero">
          <div>
            <div className="stat-value">{dashboard.totalTasks}</div>
            <div className="stat-label">Total tasks</div>
          </div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">{dashboard.todoCount}</div>
          <div className="stat-label">To do</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">{dashboard.inProgressCount}</div>
          <div className="stat-label">In progress</div>
        </div>
        <div className="card stat-card">
          <div className="stat-value">{dashboard.doneCount}</div>
          <div className="stat-label">Done</div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
        <div>
          <div className="dashboard-section">
            <h2>Overdue tasks ({dashboard.overdueTasks.length})</h2>
            {dashboard.overdueTasks.length === 0 && <div className="empty-state">Nothing overdue. Nice work.</div>}
            {dashboard.overdueTasks.map((task) => <TaskRow key={task.id} task={task} />)}
          </div>

          <div className="dashboard-section">
            <h2>High priority ({dashboard.highPriorityTasks.length})</h2>
            {dashboard.highPriorityTasks.length === 0 && <div className="empty-state">No high or urgent tasks in flight.</div>}
            {dashboard.highPriorityTasks.map((task) => <TaskRow key={task.id} task={task} />)}
          </div>

          <div className="dashboard-section">
            <h2>Assigned to you ({dashboard.myTasks.length})</h2>
            {dashboard.myTasks.length === 0 && <div className="empty-state">Nothing assigned to you right now.</div>}
            {dashboard.myTasks.map((task) => <TaskRow key={task.id} task={task} />)}
          </div>
        </div>

        <div className="dashboard-section">
          <h2>Recent activity</h2>
          <div className="card" style={{ padding: '4px 16px' }}>
            <ActivityFeed activities={activities.slice(0, 20)} />
          </div>
        </div>
      </div>
    </div>
  );
}

function TaskRow({ task }) {
  return (
    <Link to={`/tasks/${task.id}`} className="task-list-row">
      <span className={`priority-tag ${task.priority}`}>{task.priority}</span>
      <span className="title">{task.title}</span>
      {task.dueDate && <span className={task.overdue ? 'overdue-flag' : ''}>{task.dueDate}</span>}
    </Link>
  );
}
