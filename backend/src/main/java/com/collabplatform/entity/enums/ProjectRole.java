package com.collabplatform.entity.enums;

/**
 * A user's role within a specific project (stored on ProjectMember).
 * OWNER: created the project, full control, cannot be removed (must transfer/delete instead).
 * ADMIN: can manage members and tasks, cannot delete the project.
 * MEMBER: can view/create/update tasks and comments, cannot manage membership.
 */
public enum ProjectRole {
    OWNER,
    ADMIN,
    MEMBER
}
