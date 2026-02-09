package task_manager_api.service.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.Team;
import task_manager_api.model.TeamMembership;
import task_manager_api.model.TeamRole;
import task_manager_api.model.User;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;

@Service
@RequiredArgsConstructor
public class TeamAccessAuthService {

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;

    public Team requireTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }

    public TeamMembership requireMembership(Team team, User user) {
        return teamMembershipRepository.findByTeamAndUser(team, user)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this team"));
    }

    public boolean isMember(Team team, User user) {
        return teamMembershipRepository.findByTeamAndUser(team, user).isPresent();
    }

    public TeamMembership createMembership(Team team, User user, TeamRole role) {
        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setTeamRole(role);
        return teamMembershipRepository.save(membership);
    }
}
