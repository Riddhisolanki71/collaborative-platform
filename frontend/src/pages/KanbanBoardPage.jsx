import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useProjectSocket } from '../context/useProjectSocket';
import ProjectTabs from '../components/ProjectTabs';
import TaskCard from '../components/TaskCard';
import TaskFormModal from '../components/TaskFormModal';

const COLUMNS = [
  { key: 'TODO', label: 'To do' },
  { key: 'IN_PROGRESS', label: 'In progress' },
  { key: 'DONE', label: 'Done' },
];

export default function KanbanBoardPage() {
  const { projectId } = useParams();
  const [tasks, setTasks] = useState([]);
  const [error, setError] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [dragOverColumn, setDragOverColumn] = useState(null);

  const loadTasks = useCallback(() => {
    api.get(`/projects/${projectId}/tasks`).then(setTasks).catch((err) => setError(err.message));
  }, [projectId]);

  useEffect(() => {
    loadTasks();
  }, [loadTasks]);

  // Real-time: whenever anyone (including us, from another tab) creates,
  // updates, assigns, or moves a task in this project, patch our local
  // state so the board reflects it immediately - no refresh needed.
  useProjectSocket(projectId, (event) => {
    setTasks((current) => {
      switch (event.type) {
        case 'TASK_CREATED':
          if (current.some((t) => t.id === event.payload.id)) return current;
          return [...current, event.payload];
        case 'TASK_UPDATED':
        case 'TASK_STATUS_CHANGED':
        case 'TASK_ASSIGNED':
          return current.map((t) => (t.id === event.payload.id ? event.payload : t));
        case 'TASK_DELETED':
          return current.filter((t) => t.id !== event.payload);
        default:
          return current;
      }
    });
  });

  function handleDragStart(e, task) {
    e.dataTransfer.setData('text/plain', JSON.stringify({ id: task.id, version: task.version, status: task.status }));
  }

  async function handleDrop(e, columnKey) {
    e.preventDefault();
    setDragOverColumn(null);
    const data = JSON.parse(e.dataTransfer.getData('text/plain'));
    if (data.status === columnKey) return;

    // Optimistic update so the drag feels instant; the WebSocket echo (or a
    // 409 below) will reconcile it if something else changed in the meantime.
    setTasks((current) => current.map((t) => (t.id === data.id ? { ...t, status: columnKey } : t)));

    try {
      const updated = await api.patch(`/tasks/${data.id}/status`, {
        status: columnKey,
        version: data.version,
      });
      setTasks((current) => current.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err) {
      setError(err.status === 409
        ? 'That task changed elsewhere just now - refreshing it for you.'
        : err.message);
      loadTasks();
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Board</h1>
          <p className="page-subtitle">Drag a card between columns to update its status.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>New task</button>
      </div>

      <ProjectTabs projectId={projectId} />

      {error && <div className="error-banner">{error}</div>}

      <div className="board">
        {COLUMNS.map((col) => {
          const columnTasks = tasks.filter((t) => t.status === col.key);
          return (
            <div
              key={col.key}
              className={`board-column${dragOverColumn === col.key ? ' drag-over' : ''}`}
              onDragOver={(e) => { e.preventDefault(); setDragOverColumn(col.key); }}
              onDragLeave={() => setDragOverColumn(null)}
              onDrop={(e) => handleDrop(e, col.key)}
            >
              <div className="board-column-header">
                <span className={`status-dot ${col.key}`} />
                {col.label}
                <span className="column-count">{columnTasks.length}</span>
              </div>
              {columnTasks.length === 0 && <div className="empty-state" style={{ padding: '8px 4px' }}>No tasks</div>}
              {columnTasks.map((task) => (
                <TaskCard key={task.id} task={task} onDragStart={handleDragStart} />
              ))}
            </div>
          );
        })}
      </div>

      {showCreateModal && (
        <TaskFormModal
          projectId={projectId}
          onClose={() => setShowCreateModal(false)}
          onCreated={(task) => {
            setTasks((current) => [...current, task]);
            setShowCreateModal(false);
          }}
        />
      )}
    </div>
  );
}
