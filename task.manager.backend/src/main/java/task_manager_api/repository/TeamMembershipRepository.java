package task_manager_api.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import task_manager_api.model.Team;
import task_manager_api.model.TeamMembership;
import task_manager_api.model.TeamMembershipId;
import task_manager_api.model.User;

import java.util.List;
import java.util.Optional;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, TeamMembershipId> {
    List<TeamMembership> findByUser(User user);

    List<TeamMembership> findByTeam(Team team);

    Optional<TeamMembership> findByTeamAndUser(Team team, User user);

    void deleteAllByTeam(Team team);

}
