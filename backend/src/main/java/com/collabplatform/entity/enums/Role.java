package com.collabplatform.entity.enums;

/**
 * Global authentication role used by Spring Security (kept intentionally simple).
 * Fine-grained, per-project permissions are governed separately by
 * {@link ProjectRole}, which is what actually decides whether a user can
 * manage a given project's members, tasks, etc.
 */
public enum Role {
    USER,
    PROJECT_ADMIN
}
