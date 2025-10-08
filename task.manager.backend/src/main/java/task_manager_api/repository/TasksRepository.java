package task_manager_api.repository;

import task_manager_api.model.Status;
import task_manager_api.model.Tasks;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TasksRepository extends JpaRepository<Tasks, Integer> {

    List<Tasks> findByTitleContainingIgnoreCase(String keyword);

    List<Tasks> findByStatus(Status status);
}
