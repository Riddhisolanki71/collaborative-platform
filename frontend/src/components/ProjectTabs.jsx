import { NavLink } from 'react-router-dom';

export default function ProjectTabs({ projectId }) {
  return (
    <div className="project-tabs">
      <NavLink
        to={`/projects/${projectId}`}
        end
        className={({ isActive }) => `project-tab${isActive ? ' active' : ''}`}
      >
        Dashboard
      </NavLink>
      <NavLink
        to={`/projects/${projectId}/board`}
        className={({ isActive }) => `project-tab${isActive ? ' active' : ''}`}
      >
        Board
      </NavLink>
      <NavLink
        to={`/projects/${projectId}/members`}
        className={({ isActive }) => `project-tab${isActive ? ' active' : ''}`}
      >
        Members
      </NavLink>
    </div>
  );
}
