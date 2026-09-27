function timeAgo(isoString) {
  const seconds = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default function ActivityFeed({ activities }) {
  if (!activities || activities.length === 0) {
    return <div className="empty-state">No activity yet.</div>;
  }

  return (
    <div>
      {activities.map((activity) => (
        <div key={activity.id} className="activity-item">
          <div style={{ flex: 1 }}>{activity.message}</div>
          <div className="activity-time">{timeAgo(activity.createdAt)}</div>
        </div>
      ))}
    </div>
  );
}
