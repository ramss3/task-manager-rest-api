package task_manager_api.controller;

import task_manager_api.model.Status;
import task_manager_api.service.TasksService;
import jakarta.validation.Valid;
import task_manager_api.model.Tasks;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TasksController {

    private final TasksService tasksService;


    public TasksController(TasksService tasksService) {
        this.tasksService = tasksService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public void create(@Valid @RequestBody Tasks task) {
        tasksService.createTask(task);
    }

    @GetMapping("")
    public List<Tasks> getAllTasks() {
        return tasksService.getAllTasks();
    }

    @GetMapping("/search/id/{id}")
    public Tasks getTaskById(@PathVariable Integer id) {
        return tasksService.getTaskById(id);
    }

    @GetMapping("/search/title/{keyword}")
    public List<Tasks> getTasksByKeywordInTitle(@PathVariable String keyword) {
        return tasksService.findByTitle(keyword);
    }

    @GetMapping("/search/status/{status}")
    public List<Tasks> getTasksByStatus(@PathVariable Status status) {
        return tasksService.findByStatus(status);
    }

    @GetMapping("/statuses")
    public Status[] getAllStatuses() {
        return Status.values();
    }


    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/{id}")
    public void update(@Valid @RequestBody Tasks task, @PathVariable Integer id) {
        tasksService.updateTask(id, task);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        tasksService.deleteTask(id);
    }

}
