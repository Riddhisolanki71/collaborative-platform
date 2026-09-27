package com.collabplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage of the core workflow described in the README:
 * register -> create project -> add member -> create task -> assign ->
 * move status -> comment -> permission checks -> optimistic locking conflict.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectTaskFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String name, String email) throws Exception {
        Map<String, String> request = Map.of("name", name, "email", email, "password", "password123");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void fullCollaborationWorkflow() throws Exception {
        String ownerToken = registerAndLogin("Owner Olivia", "owner@example.com");
        String memberToken = registerAndLogin("Member Max", "member@example.com");

        // 1. Owner creates a project.
        Map<String, String> projectRequest = Map.of("name", "Website Revamp", "description", "Q4 redesign");
        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andReturn();
        long projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString()).get("id").asLong();

        // A non-member cannot view the project yet.
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // 2. Owner adds Max as a MEMBER.
        Map<String, Object> addMemberRequest = Map.of("email", "member@example.com", "role", "MEMBER");
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        // A MEMBER is not authorized to add other members (only OWNER/ADMIN can).
        Map<String, Object> anotherAddRequest = Map.of("email", "owner@example.com", "role", "ADMIN");
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anotherAddRequest)))
                .andExpect(status().isForbidden()); // MEMBER role cannot manage membership

        // Owner attempting to add the same user (Max) again is rejected as a duplicate.
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isConflict());

        // 3. Owner creates a task and assigns it to Max.
        Map<String, Object> taskRequest = Map.of(
                "title", "API Integration",
                "description", "Wire up the payments API",
                "priority", "HIGH"
        );
        MvcResult taskResult = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andReturn();
        JsonNode taskNode = objectMapper.readTree(taskResult.getResponse().getContentAsString());
        long taskId = taskNode.get("id").asLong();
        long version = taskNode.get("version").asLong();

        // Find Max's user id via the members list to assign the task to him.
        MvcResult membersResult = mockMvc.perform(get("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andReturn();
        JsonNode members = objectMapper.readTree(membersResult.getResponse().getContentAsString());
        long maxUserId = -1;
        for (JsonNode m : members) {
            if (m.get("user").get("email").asText().equals("member@example.com")) {
                maxUserId = m.get("user").get("id").asLong();
            }
        }

        MvcResult assignResult = mockMvc.perform(patch("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assigneeId", maxUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.email").value("member@example.com"))
                .andReturn();
        // Assigning updates the row, which bumps the optimistic-lock version - re-read it
        // before the next update, the same way a real client would refetch after a change.
        version = objectMapper.readTree(assignResult.getResponse().getContentAsString()).get("version").asLong();

        // 4. Max moves the task from TODO to IN_PROGRESS using the (still current) version.
        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS", "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 5. Optimistic locking: trying to update again with the now-stale version is rejected with 409.
        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE", "version", version))))
                .andExpect(status().isConflict());

        // 6. Max adds a comment on the task.
        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Started working on this."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Started working on this."));

        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author.name").value("Member Max"));

        // 7. Activity history recorded the key events.
        mockMvc.perform(get("/api/projects/" + projectId + "/activities")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'TASK_STATUS_CHANGED')]").exists());

        // 8. Dashboard reflects the current board state.
        mockMvc.perform(get("/api/projects/" + projectId + "/dashboard")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.inProgressCount").value(1));

        // 9. Comment author received a notification-worthy event; Max's own notifications list is empty
        // (he authored the comment himself) but the assignment earlier should have notified him.
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'TASK_ASSIGNED')]").exists());

        // 10. A MEMBER cannot delete the project (only the OWNER can).
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTaskInputIsRejected() throws Exception {
        String ownerToken = registerAndLogin("Solo Owner", "solo@example.com");

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Solo Project"))))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString()).get("id").asLong();

        // Blank title should fail validation.
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", ""))))
                .andExpect(status().isBadRequest());
    }
}
