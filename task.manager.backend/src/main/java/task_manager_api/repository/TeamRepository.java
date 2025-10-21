package task_manager_api.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import task_manager_api.model.Team;
import java.util.Optional;

public interface TeamRepository extends JpaRepositoryImplementation<Team,Long> {
    Optional<Team> findByName(String name);
    boolean existsByName(String name);
}
