package com.collabplatform.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Actually sends a WsBroadcastEvent over STOMP, but only after the database
 * transaction that produced it has committed successfully.
 *
 * Why this matters: every write path (TaskService, CommentService, etc.) logs
 * an activity and/or a notification and pushes a WebSocket event, all inside
 * the same @Transactional method as the underlying change. If the broadcast
 * were sent synchronously (as a direct SimpMessagingTemplate call) it would
 * go out *before* Spring's transactional advice commits the surrounding
 * transaction - so (a) a client could receive a "task updated" push and
 * immediately GET /api/tasks/{id} on a different DB connection and, depending
 * on timing, not yet see the committed row, and (b) if something later in the
 * same transaction throws and the whole thing rolls back, clients would still
 * have received an event for a change that never actually happened.
 *
 * TransactionPhase.AFTER_COMMIT guarantees the event fires only once the
 * transaction has committed, and simply doesn't fire at all if it rolls back.
 * fallbackExecution = true keeps the same behavior working for any call site
 * that happens to run outside of a transaction (e.g. a future admin/batch
 * job), where it just sends immediately instead of being silently dropped.
 */
@Component
@RequiredArgsConstructor
public class WsBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWsBroadcast(WsBroadcastEvent event) {
        messagingTemplate.convertAndSend(event.getDestination(), event.getMessage());
    }
}
