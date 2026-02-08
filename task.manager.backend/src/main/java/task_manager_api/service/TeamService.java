package task_manager_api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.TeamUpdateDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.mapper.TaskMapper;
import task_manager_api.mapper.TeamMapper;
import task_manager_api.mapper.UserMapper;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;
import task_manager_api.repository.UserRepository;

import java.util.List;


@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TasksRepository tasksRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserService userService;
    private final UserLookupService userLookupService;
    private final TeamMembershipPolicy membershipPolicy;

    // --- Team ---
    @Transactional
    public TeamResponseDTO createTeam(TeamCreateDTO dto) {
        // When creating a team the creator automatically integrates the team
        User user = requireLoggedUser();

        Team team = new Team();
        team.setName(dto.getTeamName());
        Team createdTeam = teamRepository.save(team);

        TeamMembership ownerMembership = createMembership(createdTeam, user, TeamRole.OWNER);
        createdTeam.getMemberships().add(ownerMembership);
        return TeamMapper.toResponseDTO(createdTeam);
    }

    public List<TeamResponseDTO> getAllTeamsForUser() {
        return teamMembershipRepository.findByUser(requireLoggedUser())
                .stream()
                .map(TeamMembership::getTeam)
                .map(TeamMapper::toResponseDTO)
                .toList();
    }

    @Transactional
    public TeamResponseDTO updateTeam(Long teamId, TeamUpdateDTO updatedTeam) {
        User loggedUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        TeamMembership membership = requireMembership(team, loggedUser);
        requireOwner(membership, "Only the owner can update the team");

        team.setName(updatedTeam.getTeamName());
        return TeamMapper.toResponseDTO(teamRepository.save(team));
    }

    @Transactional
    public void deleteTeam(Long teamId) {
        User loggedUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        TeamMembership membership = requireMembership(team, loggedUser);
        requireOwner(membership, "Only the team owner can delete the team");

        teamMembershipRepository.deleteAllByTeam(team);
        teamRepository.delete(team);
    }

    // Team members
    @Transactional
    public TeamResponseDTO addUserToTeam(Long teamId, String identifier, TeamRole role) {
        User currUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        TeamMembership loggedMembership = requireMembership(team, currUser);
        membershipPolicy.requireCanManageMembers(loggedMembership, "Only the owner or admins can add new user to the team");

        TeamRole desiredRole = role != null ? role : TeamRole.MEMBER;

        if(!loggedMembership.getTeamRole().isOwner() && desiredRole.isOwner()) {
            throw new UnauthorizedActionException("Only the owner can assign a new owner");
        }

        User newUser = userLookupService.searchByIdentifier(identifier);

        if(teamMembershipRepository.existsByTeamAndUser(team, newUser)) {
            throw new ConflictException("User is already in the team");
        }

        TeamMembership teamMembership = createMembership(team, newUser, desiredRole);
        team.getMemberships().add(teamMembership);
        teamRepository.save(team);
        return TeamMapper.toResponseDTO(team);
    }

    @Transactional
    public TeamResponseDTO updateUserRole(Long teamId, Long userId, TeamRole newRole) {
        if(newRole == null) throw new ConflictException("New role cannot be null");

        User currUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        TeamMembership loggedMembership = requireMembership(team, currUser);
        membershipPolicy.requireCanManageMembers(loggedMembership,
                "Only the owner or admins can update a user role");

        User userToUpdate = requireUser(userId);
        TeamMembership membershipToUpdate = requireMembership(team, userToUpdate);

        membershipPolicy.validateRoleChange(currUser, loggedMembership, userToUpdate, membershipToUpdate, newRole);

        membershipToUpdate.setTeamRole(newRole);
        teamMembershipRepository.save(membershipToUpdate);
        return TeamMapper.toResponseDTO(team);
    }

    @Transactional
    public void removeUserFromTeam(Long teamId, Long userId) {
        User currUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        TeamMembership loggedMembership = requireMembership(team, currUser);
        membershipPolicy.requireCanManageMembers(loggedMembership, "Only the owner or admins can delete a user");

        User targetUser  = requireUser(userId);
        TeamMembership targetMembership = requireMembership(team, targetUser);

        if (targetMembership.getTeamRole().isOwner()) {
            throw new UnauthorizedActionException("You cannot remove the team owner");
        }

        teamMembershipRepository.delete(targetMembership);
        team.getMemberships().remove(targetMembership);
        teamRepository.save(team);
    }

    public List<UserMemberDTO> getTeamMembers(Long teamId) {
        User currUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        requireMembership(team, currUser);

        return teamMembershipRepository.findByTeam(team)
                .stream()
                .map(m -> UserMapper.toMemberDTO(m.getUser(), m.getTeamRole()))
                .toList();
    }

    // Tasks
    public List<TaskSummaryDTO> getTeamTasks(Long teamId) {
        User currUser = requireLoggedUser();
        Team team = requireTeam(teamId);

        requireMembership(team, currUser);
        return tasksRepository.findByTeam(team)
                .stream()
                .map(TaskMapper::toSummaryDTO)
                .toList();
    }

    // Helpers
    private User requireLoggedUser() {
        return userService.getLoggedUser();
    }

    private Team requireTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private TeamMembership requireMembership(Team team, User user) {
        return teamMembershipRepository.findByTeamAndUser(team, user)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this team"));
    }

    private void requireOwner(TeamMembership membership, String msg) {
        if (!membership.getTeamRole().isOwner()) {
            throw new UnauthorizedActionException(msg);
        }
    }

    private TeamMembership createMembership(Team team, User user, TeamRole role) {
        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setTeamRole(role);
        return teamMembershipRepository.save(membership);
    }
}
