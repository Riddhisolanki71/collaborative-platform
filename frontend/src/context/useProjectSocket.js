import { useEffect, useRef } from 'react';
import { createStompClient } from '../api/websocket';

/**
 * Subscribes to /topic/projects/{projectId} for as long as the component using
 * it is mounted (i.e. while the user is actually viewing that project), and
 * routes each incoming WsEvent to onEvent. This is what makes the Kanban
 * board, activity feed, etc. update live without a page refresh when another
 * user makes a change.
 */
export function useProjectSocket(projectId, onEvent) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!projectId) return;

    const client = createStompClient();
    let subscription;

    client.onConnect = () => {
      subscription = client.subscribe(`/topic/projects/${projectId}`, (message) => {
        try {
          const event = JSON.parse(message.body);
          onEventRef.current?.(event);
        } catch {
          // ignore malformed frames
        }
      });
    };

    client.activate();

    return () => {
      subscription?.unsubscribe();
      client.deactivate();
    };
  }, [projectId]);
}
