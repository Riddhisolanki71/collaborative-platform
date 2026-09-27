import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';

export default function ProjectListPage() {
  const [projects, setProjects] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/projects').then(setProjects).catch((err) => setError(err.message));
  }, []);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Your projects</h1>
          <p className="page-subtitle">Everything you own or have been added to.</p>
        </div>
        <Link to="/projects/new" className="btn btn-primary">New project</Link>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {projects && projects.length === 0 && (
        <div className="empty-state">
          You're not part of any projects yet. Create one to get started.
        </div>
      )}

      {projects && projects.length > 0 && (
        <div className="project-grid">
          {projects.map((project) => (
            <Link to={`/projects/${project.id}`} key={project.id} className="card project-card">
              <h3>{project.name}</h3>
              <p>{project.description || 'No description yet.'}</p>
              <div className="project-card-meta">
                <span className="role-tag">{project.myRole}</span>
                <span>Owner: {project.owner.name}</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
