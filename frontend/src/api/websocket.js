import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getToken } from './client';

const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

/**
 * Creates (but does not yet activate) a STOMP client over SockJS, authenticated
 * with the same JWT used for REST calls. Callers activate it, subscribe to the
 * destinations they care about, and deactivate it on unmount.
 */
export function createStompClient() {
  const token = getToken();

  return new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 4000,
    debug: () => {}, // silence verbose STOMP frame logging in the console
  });
}
