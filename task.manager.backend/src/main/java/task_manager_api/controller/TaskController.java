package task_manager_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import task_manager_api.DTO.task.TaskCreateDTO;
import task_manager_api.DTO.task.TaskResponseDTO;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.task.TaskUpdateDTO;
import task_manager_api.model.Status;
import task_manager_api.service.task.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("isAuthenticated()")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(@Valid @RequestBody TaskCreateDTO dto) {
        TaskResponseDTO task = taskService.createTask(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @GetMapping
    public List<TaskSummaryDTO> getTasksForUser() {
        return taskService.getUserTasks();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@PathVariable Integer id) {
        TaskResponseDTO task = taskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

    @GetMapping("/team/{teamId}")
    public List<TaskResponseDTO> getTasksByTeam(@PathVariable Long teamId) {
        return taskService.getTasksByTeam(teamId);
    }

    @GetMapping("/search/title/{keyword}")
    public List<TaskSummaryDTO> getTasksByKeywordInTitle(@PathVariable String keyword) {
        return taskService.findByTitle(keyword);
    }

    @GetMapping("/search/status/{status}")
    public List<TaskSummaryDTO> getTasksByStatus(@PathVariable Status status) {
        return taskService.findByStatus(status);
    }

    @GetMapping("/statuses")
    public Status[] getAllStatuses() {
        return Status.values();
    }


    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> update(@PathVariable Integer id,
                                                  @Valid @RequestBody TaskUpdateDTO task) {
        TaskResponseDTO updatedTask = taskService.updateTask(id, task);
        return ResponseEntity.ok(updatedTask);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        taskService.deleteTask(id);
    }

}
