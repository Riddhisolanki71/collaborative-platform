import { useEffect, useRef } from 'react';
import { createStompClient } from '../api/websocket';

/**
 * Subscribes to the current user's personal notification feed
 * (/topic/users/{userId}/notifications) for the lifetime of the app shell,
 * so a badge / toast can appear the instant something relevant happens,
 * even on pages that aren't the project the event came from.
 */
export function useNotificationsSocket(userId, onNotification) {
  const onNotificationRef = useRef(onNotification);
  onNotificationRef.current = onNotification;

  useEffect(() => {
    if (!userId) return;

    const client = createStompClient();
    let subscription;

    client.onConnect = () => {
      subscription = client.subscribe(`/topic/users/${userId}/notifications`, (message) => {
        try {
          const event = JSON.parse(message.body);
          onNotificationRef.current?.(event.payload);
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
  }, [userId]);
}
