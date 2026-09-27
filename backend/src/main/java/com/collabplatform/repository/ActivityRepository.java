package com.collabplatform.repository;

import com.collabplatform.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
