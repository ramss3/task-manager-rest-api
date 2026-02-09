package task_manager_api.service.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.TeamUpdateDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.mapper.TaskMapper;
import task_manager_api.mapper.TeamMapper;
import task_manager_api.mapper.UserMapper;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;
import task_manager_api.service.user.UserLookupService;
import task_manager_api.service.user.UserService;

import java.util.List;


@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TasksRepository tasksRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserService userService;
    private final UserLookupService userLookupService;
    private final TeamMembershipPolicy membershipPolicy;
    private final TeamAccessAuthService teamAccessAuthService;

    // --- Team ---
    @Transactional
    public TeamResponseDTO createTeam(TeamCreateDTO dto) {
        // When creating a team the creator automatically integrates the team
        User user = userService.getLoggedUser();

        Team team = new Team();
        team.setName(dto.getName());
        Team createdTeam = teamRepository.save(team);

        TeamMembership ownerMembership = teamAccessAuthService.createMembership(createdTeam, user, TeamRole.OWNER);
        createdTeam.getMemberships().add(ownerMembership);
        return TeamMapper.toResponseDTO(createdTeam);
    }

    public List<TeamResponseDTO> getAllTeamsForUser() {
        return teamMembershipRepository.findByUser(userService.getLoggedUser())
                .stream()
                .map(TeamMembership::getTeam)
                .map(TeamMapper::toResponseDTO)
                .toList();
    }

    @Transactional
    public TeamResponseDTO updateTeam(Long teamId, TeamUpdateDTO updatedTeam) {
        User loggedUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        TeamMembership membership = teamAccessAuthService.requireMembership(team, loggedUser);
        requireOwner(membership, "Only the owner can update the team");

        team.setName(updatedTeam.getTeamName());
        return TeamMapper.toResponseDTO(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(Long teamId) {
        User loggedUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        TeamMembership membership = teamAccessAuthService.requireMembership(team, loggedUser);
        requireOwner(membership, "Only the team owner can delete the team");

        teamMembershipRepository.deleteAllByTeam(team);
        teamRepository.delete(team);
    }

    // Team members
    @Transactional
    public TeamResponseDTO addUserToTeam(Long teamId, String identifier, TeamRole role) {
        User currUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        TeamMembership loggedMembership = teamAccessAuthService.requireMembership(team, currUser);
        membershipPolicy.requireCanManageMembers(loggedMembership, "Only the owner or admins can add new user to the team");

        TeamRole desiredRole = role != null ? role : TeamRole.MEMBER;

        if(!loggedMembership.getTeamRole().isOwner() && desiredRole.isOwner()) {
            throw new UnauthorizedActionException("Only the owner can assign a new owner");
        }

        User newUser = userLookupService.searchByIdentifier(identifier);

        if(teamAccessAuthService.isMember(team, newUser)) {
            throw new ConflictException("User is already in the team");
        }

        TeamMembership teamMembership = teamAccessAuthService.createMembership(team, newUser, desiredRole);
        team.getMemberships().add(teamMembership);
        teamRepository.save(team);
        return TeamMapper.toResponseDTO(team);
    }

    @Transactional
    public TeamResponseDTO updateUserRole(Long teamId, Long userId, TeamRole newRole) {
        if(newRole == null) throw new ConflictException("New role cannot be null");

        User currUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        TeamMembership loggedMembership = teamAccessAuthService.requireMembership(team, currUser);
        membershipPolicy.requireCanManageMembers(loggedMembership,
                "Only the owner or admins can update a user role");

        User userToUpdate = userLookupService.requireUser(userId);
        TeamMembership membershipToUpdate = teamAccessAuthService.requireMembership(team, userToUpdate);

        membershipPolicy.validateRoleChange(currUser, loggedMembership, userToUpdate, membershipToUpdate, newRole);

        membershipToUpdate.setTeamRole(newRole);
        teamMembershipRepository.save(membershipToUpdate);
        return TeamMapper.toResponseDTO(team);
    }

    @Transactional
    public void removeUserFromTeam(Long teamId, Long userId) {
        User currUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        TeamMembership loggedMembership = teamAccessAuthService.requireMembership(team, currUser);
        membershipPolicy.requireCanManageMembers(loggedMembership, "Only the owner or admins can delete a user");

        User targetUser  = userLookupService.requireUser(userId);
        TeamMembership targetMembership = teamAccessAuthService.requireMembership(team, targetUser);

        if (targetMembership.getTeamRole().isOwner()) {
            throw new UnauthorizedActionException("You cannot remove the team owner");
        }

        teamMembershipRepository.delete(targetMembership);
        team.getMemberships().remove(targetMembership);
        teamRepository.save(team);
    }

    public List<UserMemberDTO> getTeamMembers(Long teamId) {
        User currUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        teamAccessAuthService.requireMembership(team, currUser);

        return teamMembershipRepository.findByTeam(team)
                .stream()
                .map(m -> UserMapper.toMemberDTO(m.getUser(), m.getTeamRole()))
                .toList();
    }

    // Tasks
    public List<TaskSummaryDTO> getTeamTasks(Long teamId) {
        User currUser = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        teamAccessAuthService.requireMembership(team, currUser);
        return tasksRepository.findByTeam(team)
                .stream()
                .map(TaskMapper::toSummaryDTO)
                .toList();
    }

    // Helpers
    private void requireOwner(TeamMembership membership, String msg) {
        if (!membership.getTeamRole().isOwner()) {
            throw new UnauthorizedActionException(msg);
        }
    }
}
