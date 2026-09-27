package com.collabplatform.repository;

import com.collabplatform.entity.Task;
import com.collabplatform.entity.enums.Priority;
import com.collabplatform.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    List<Task> findByAssigneeId(Long assigneeId);

    List<Task> findByCreatorId(Long creatorId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    long countByProjectId(Long projectId);

    @Query("select count(t) from Task t where t.project.id = :projectId and t.priority = :priority and t.status <> com.collabplatform.entity.enums.TaskStatus.DONE")
    long countActiveHighPriority(@Param("projectId") Long projectId, @Param("priority") Priority priority);

    @Query("select t from Task t where t.project.id = :projectId and t.dueDate is not null " +
           "and t.dueDate < :today and t.status <> com.collabplatform.entity.enums.TaskStatus.DONE")
    List<Task> findOverdueForProject(@Param("projectId") Long projectId, @Param("today") LocalDate today);

    @Query("select t from Task t where t.assignee.id = :userId and t.dueDate is not null " +
           "and t.dueDate < :today and t.status <> com.collabplatform.entity.enums.TaskStatus.DONE")
    List<Task> findOverdueForUser(@Param("userId") Long userId, @Param("today") LocalDate today);
}
