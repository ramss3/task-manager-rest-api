package task_manager_api.repository;

import task_manager_api.model.Status;
import task_manager_api.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import task_manager_api.model.Team;
import task_manager_api.model.User;

import java.util.List;

public interface TasksRepository extends JpaRepository<Task, Integer> {

    List<Task> findByTitleContainingIgnoreCase(String keyword);

    List<Task> findByStatus(Status status);

    List<Task> findByUser(User user);

    List<Task> findByTeam(Team team);


}
