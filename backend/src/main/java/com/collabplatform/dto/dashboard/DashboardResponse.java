package com.collabplatform.dto.dashboard;

import com.collabplatform.dto.task.TaskResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DashboardResponse {
    private long totalTasks;
    private long todoCount;
    private long inProgressCount;
    private long doneCount;
    private List<TaskResponse> overdueTasks;
    private List<TaskResponse> highPriorityTasks;
    private List<TaskResponse> myTasks;
}
