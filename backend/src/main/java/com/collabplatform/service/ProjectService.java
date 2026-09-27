package com.collabplatform.service;

import com.collabplatform.dto.project.ProjectMemberRequest;
import com.collabplatform.dto.project.ProjectMemberResponse;
import com.collabplatform.dto.project.ProjectRequest;
import com.collabplatform.dto.project.ProjectResponse;
import com.collabplatform.dto.user.UserResponse;
import com.collabplatform.dto.ws.WsEventType;
import com.collabplatform.entity.Project;
import com.collabplatform.entity.ProjectMember;
import com.collabplatform.entity.User;
import com.collabplatform.entity.enums.ActivityType;
import com.collabplatform.entity.enums.ProjectRole;
import com.collabplatform.exception.BadRequestException;
import com.collabplatform.exception.ConflictException;
import com.collabplatform.exception.ForbiddenException;
import com.collabplatform.exception.ResourceNotFoundException;
import com.collabplatform.repository.ProjectMemberRepository;
import com.collabplatform.repository.ProjectRepository;
import com.collabplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final WebSocketEventPublisher eventPublisher;

    @Transactional
    public ProjectResponse createProject(ProjectRequest request, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();
        project = projectRepository.save(project);

        ProjectMember membership = ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectRole.OWNER)
                .build();
        projectMemberRepository.save(membership);

        activityService.record(project, owner, null, ActivityType.PROJECT_CREATED,
                owner.getName() + " created the project \"" + project.getName() + "\".");

        return toResponse(project, ProjectRole.OWNER);
    }

    public List<ProjectResponse> listForUser(Long userId) {
        return projectRepository.findAllForUser(userId).stream()
                .map(p -> {
                    ProjectRole role = projectMemberRepository.findByProjectIdAndUserId(p.getId(), userId)
                            .map(ProjectMember::getRole).orElse(null);
                    return toResponse(p, role);
                })
                .toList();
    }

    public ProjectResponse getProject(Long projectId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        ProjectMember membership = requireMembership(projectId, userId);
        return toResponse(project, membership.getRole());
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectRequest request, Long userId) {
        Project project = findProjectOrThrow(projectId);
        ProjectMember membership = requireMembership(projectId, userId);
        requireAdminOrOwner(membership);

        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project = projectRepository.save(project);

        activityService.record(project, membership.getUser(), null, ActivityType.PROJECT_UPDATED,
                membership.getUser().getName() + " updated the project details.");

        eventPublisher.publishToProject(projectId, WsEventType.PROJECT_UPDATED, toResponse(project, membership.getRole()));

        return toResponse(project, membership.getRole());
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        Project project = findProjectOrThrow(projectId);
        if (!project.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("Only the project owner can delete the project");
        }
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request, Long actingUserId) {
        Project project = findProjectOrThrow(projectId);
        ProjectMember actingMembership = requireMembership(projectId, actingUserId);
        requireAdminOrOwner(actingMembership);

        User userToAdd = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email " + request.getEmail()));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userToAdd.getId())) {
            throw new ConflictException("This user is already a member of the project");
        }

        if (request.getRole() == ProjectRole.OWNER) {
            throw new BadRequestException("Cannot add a member directly as OWNER");
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(userToAdd)
                .role(request.getRole())
                .build();
        member = projectMemberRepository.save(member);

        activityService.record(project, actingMembership.getUser(), null, ActivityType.MEMBER_ADDED,
                actingMembership.getUser().getName() + " added " + userToAdd.getName() + " to the project.");

        ProjectMemberResponse response = toMemberResponse(member);
        eventPublisher.publishToProject(projectId, WsEventType.MEMBER_ADDED, response);

        return response;
    }

    @Transactional
    public void removeMember(Long projectId, Long memberUserId, Long actingUserId) {
        Project project = findProjectOrThrow(projectId);
        ProjectMember actingMembership = requireMembership(projectId, actingUserId);
        requireAdminOrOwner(actingMembership);

        if (project.getOwner().getId().equals(memberUserId)) {
            throw new BadRequestException("The project owner cannot be removed");
        }

        ProjectMember target = projectMemberRepository.findByProjectIdAndUserId(projectId, memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("This user is not a member of the project"));

        projectMemberRepository.delete(target);

        activityService.record(project, actingMembership.getUser(), null, ActivityType.MEMBER_REMOVED,
                actingMembership.getUser().getName() + " removed " + target.getUser().getName() + " from the project.");

        eventPublisher.publishToProject(projectId, WsEventType.MEMBER_REMOVED, memberUserId);
    }

    public List<ProjectMemberResponse> listMembers(Long projectId, Long userId) {
        requireMembership(projectId, userId);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    // ---- shared authorization helpers (also used by TaskService / CommentService) ----

    public Project findProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    /** Any project member (OWNER/ADMIN/MEMBER) can view and work with tasks/comments. */
    public ProjectMember requireMembership(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this project"));
    }

    /** Only OWNER/ADMIN may manage members, edit project info, or reassign tasks to others. */
    public void requireAdminOrOwner(ProjectMember membership) {
        if (membership.getRole() == ProjectRole.MEMBER) {
            throw new ForbiddenException("This action requires an ADMIN or OWNER role in the project");
        }
    }

    private ProjectResponse toResponse(Project project, ProjectRole myRole) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                UserResponse.from(project.getOwner()),
                myRole != null ? myRole.name() : null,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private ProjectMemberResponse toMemberResponse(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                UserResponse.from(member.getUser()),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
