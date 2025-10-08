package task_manager_api.service;

import task_manager_api.model.Status;
import task_manager_api.repository.TasksRepository;
import task_manager_api.model.Tasks;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class TasksService {

    private final TasksRepository tasksRepository;

    public TasksService(TasksRepository tasksRepository) {
        this.tasksRepository = tasksRepository;
    }

    public Tasks createTask(Tasks task) {
        return tasksRepository.save(task);
    }

    public List<Tasks> getAllTasks() {
        return tasksRepository.findAll();
    }

    public Tasks getTaskById(Integer id) {
        return tasksRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found"));
    }

    public List<Tasks> findByTitle(String keyword) {
        return tasksRepository.findByTitleContainingIgnoreCase(keyword);
    }

    public List<Tasks> findByStatus(Status status) {
        return tasksRepository.findByStatus(status);
    }

    public Tasks updateTask(Integer id, Tasks taskToBeUpdated) {
        if(!tasksRepository.existsById(id)) {
            throw new RuntimeException("Task not found");
        }
        Tasks task = getTaskById(id);
        task.setTitle(taskToBeUpdated.getTitle());
        task.setDescription(taskToBeUpdated.getDescription());
        task.setStatus(taskToBeUpdated.getStatus());
        task.setDeadline(taskToBeUpdated.getDeadline());
        return tasksRepository.save(task);
    }

    public void deleteTask(Integer id) {
        tasksRepository.deleteById(id);
    }



}
