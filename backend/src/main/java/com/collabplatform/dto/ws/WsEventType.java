package com.collabplatform.dto.ws;

/** Central catalog of real-time WebSocket event type strings. See README "Real-time event design". */
public final class WsEventType {
    private WsEventType() {}

    public static final String TASK_CREATED = "TASK_CREATED";
    public static final String TASK_UPDATED = "TASK_UPDATED";
    public static final String TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TASK_DELETED = "TASK_DELETED";
    public static final String COMMENT_ADDED = "COMMENT_ADDED";
    public static final String ACTIVITY_CREATED = "ACTIVITY_CREATED";
    public static final String MEMBER_ADDED = "MEMBER_ADDED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String PROJECT_UPDATED = "PROJECT_UPDATED";
    public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";
}
