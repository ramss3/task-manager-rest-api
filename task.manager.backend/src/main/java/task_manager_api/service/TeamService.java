package task_manager_api.service;

import org.springframework.stereotype.Service;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.TeamUpdateDTO;
import task_manager_api.DTO.team.UserMemberDTO;
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
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TasksRepository tasksRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserService userService;

    public TeamService(TeamRepository teamRepository,
                       UserRepository userRepository,
                       TasksRepository tasksRepository,
                       TeamMembershipRepository teamMembershipRepository,
                       UserService userService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.tasksRepository = tasksRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.userService = userService;
    }

    public TeamResponseDTO createTeam(TeamCreateDTO dto) {
        // When creating a team the creator automatically integrates the team
        User user = userService.getLoggedUser();
        Team team = new Team();
        team.setName(dto.getTeamName());

        Team createdTeam = teamRepository.save(team);

        TeamMembership ownerMembership = new TeamMembership();
        ownerMembership.setTeam(createdTeam);
        ownerMembership.setUser(user);
        ownerMembership.setTeamRole(TeamRole.OWNER);

        teamMembershipRepository.save(ownerMembership);
        createdTeam.getMemberships().add(ownerMembership);

        return TeamMapper.toResponseDTO(createdTeam);
    }

    public List<TeamResponseDTO> getAllTeamsForUser() {
        User user = userService.getLoggedUser();
        return teamMembershipRepository.findByUser(user)
                .stream()
                .map(TeamMembership::getTeam)
                .map(TeamMapper::toResponseDTO)
                .toList();
    }

    public TeamResponseDTO addUserToTeam(Long teamId, Long userId, TeamRole role) {
        User currUser = userService.getLoggedUser();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        TeamMembership loggedMembership = teamMembershipRepository.findByTeamAndUser(team, currUser)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this team"));

        if(loggedMembership.getTeamRole() == TeamRole.MEMBER) {
            throw new UnauthorizedActionException("Only the owner or admins can add new user to the team");
        }

        if(loggedMembership.getTeamRole() == TeamRole.ADMIN && role == TeamRole.OWNER) {
            throw new UnauthorizedActionException("Only the owner can assign a new owner");
        }

        User newUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if(teamMembershipRepository.existsByTeamAndUser(team, newUser)) {
            throw new IllegalArgumentException("User is already in the team");
        }

        TeamMembership teamMembership = new TeamMembership();
        teamMembership.setTeam(team);
        teamMembership.setUser(newUser);
        teamMembership.setTeamRole(role != null ? role : TeamRole.MEMBER);

        teamMembershipRepository.save(teamMembership);
        team.getMemberships().add(teamMembership);
        teamRepository.save(team);

        return TeamMapper.toResponseDTO(team);
    }

    public TeamResponseDTO updateUserRole(Long teamId, Long userId, TeamRole newRole) {
        User currUser = userService.getLoggedUser();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        TeamMembership loggedMembership = teamMembershipRepository.findByTeamAndUser(team, currUser)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this team"));

        if(loggedMembership.getTeamRole() == TeamRole.MEMBER) {
            throw new UnauthorizedActionException("Only the owner or admins can update a user role");
        }

        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TeamMembership membershipToUpdate = teamMembershipRepository.findByTeamAndUser(team, userToUpdate)
                .orElseThrow(() -> new UnauthorizedActionException("The desired user is not a member of this team"));

        if((loggedMembership.getTeamRole() == TeamRole.ADMIN
                && newRole == TeamRole.OWNER)
                || (membershipToUpdate.getTeamRole() == TeamRole.OWNER
                && loggedMembership.getTeamRole() != TeamRole.OWNER)) {
            throw new UnauthorizedActionException("Only the owner can modify ownership role");
        }

        if(loggedMembership.getTeamRole() == TeamRole.OWNER && currUser.equals(userToUpdate) && newRole != TeamRole.OWNER) {
            throw new UnauthorizedActionException("Owner cannot demote themselves");
        }

        membershipToUpdate.setTeamRole(newRole);
        teamMembershipRepository.save(membershipToUpdate);
        return TeamMapper.toResponseDTO(team);
    }

    public TeamResponseDTO removeUserFromTeam(Long teamId, Long userId) {
        User currUser = userService.getLoggedUser();

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));


        TeamMembership loggedMembership = teamMembershipRepository.findByTeamAndUser(team, currUser)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this team"));

        if(loggedMembership.getTeamRole() == TeamRole.MEMBER) {
            throw new UnauthorizedActionException("Only the owner or admins can delete a user");
        }

        User userToBeRemoved = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TeamMembership membership = teamMembershipRepository.findByTeamAndUser(team, userToBeRemoved)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        if (membership.getTeamRole() == TeamRole.OWNER) {
            throw new UnauthorizedActionException("You cannot remove the team owner");
        }

        teamMembershipRepository.delete(membership);
        team.getMemberships().remove(membership);
        teamRepository.save(team);

        return TeamMapper.toResponseDTO(team);
    }

    public List<UserMemberDTO> getTeamMembers(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        return teamMembershipRepository.findByTeam(team)
                .stream()
                .map(m -> UserMapper.toMemberDTO(m.getUser(), m.getTeamRole()))
                .toList();
    }

    public List<TaskSummaryDTO> getTeamTasks(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        return tasksRepository.findByTeam(team)
                .stream()
                .map(TaskMapper::toSummaryDTO)
                .toList();
    }

    public TeamResponseDTO updateTeam(Long teamId, TeamUpdateDTO updatedTeam) {
        User loggedUser = userService.getLoggedUser();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        TeamMembership membership = teamMembershipRepository.findByTeamAndUser(team, loggedUser)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of the team"));

        if(membership.getTeamRole() != TeamRole.OWNER) {
            throw new UnauthorizedActionException("Only the owner can update the team");
        }

        team.setName(updatedTeam.getTeamName());

        return TeamMapper.toResponseDTO(teamRepository.save(team));
    }

    public void deleteTeam(Long teamId) {
        User currUser = userService.getLoggedUser();
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        TeamMembership membership = teamMembershipRepository.findByTeamAndUser(team, currUser)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of the team"));

        if(membership.getTeamRole() != TeamRole.OWNER) {
            throw new UnauthorizedActionException("Only the team owner can delete the team");
        }

        teamMembershipRepository.deleteAllByTeam(team);
        teamRepository.delete(team);
    }
}
