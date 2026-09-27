import { useNavigate } from 'react-router-dom';

export default function TaskCard({ task, onDragStart }) {
  const navigate = useNavigate();

  return (
    <div
      className="task-card"
      draggable
      onDragStart={(e) => onDragStart(e, task)}
      onClick={() => navigate(`/tasks/${task.id}`)}
    >
      <div className="task-card-title">{task.title}</div>
      <div className="task-card-meta">
        <span className={`priority-tag ${task.priority}`}>{task.priority}</span>
        <span className="assignee-chip">{task.assignee ? task.assignee.name : 'Unassigned'}</span>
      </div>
      {task.dueDate && (
        <div style={{ marginTop: 8, fontSize: 12 }} className={task.overdue ? 'overdue-flag' : ''}>
          Due {task.dueDate}{task.overdue ? ' (overdue)' : ''}
        </div>
      )}
    </div>
  );
}
