package task_manager_api.service.task;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import task_manager_api.DTO.task.TaskCreateDTO;
import task_manager_api.DTO.task.TaskResponseDTO;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.task.TaskUpdateDTO;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.mapper.TaskMapper;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import org.springframework.stereotype.Service;
import task_manager_api.service.team.TeamAccessAuthService;
import task_manager_api.service.user.UserService;


import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TasksRepository tasksRepository;
    private final UserService userService;
    private final TeamAccessAuthService teamAccessAuthService;

    @Transactional
    public TaskResponseDTO createTask(TaskCreateDTO newTask) {
        User user = userService.getLoggedUser();
        Team team = null;

        if(newTask.getTeamId() != null) {
            team = teamAccessAuthService.requireTeam(newTask.getTeamId());
            teamAccessAuthService.requireMembership(team, user);
        }

        Task task = TaskMapper.toEntity(newTask, user, team);
        Task saved = tasksRepository.save(task);

        return TaskMapper.toResponseDTO(saved);
    }

    public List<TaskSummaryDTO> getUserTasks() {
        User user = userService.getLoggedUser();
        List<Task> tasks = tasksRepository.findByUser(user);
        return TaskMapper.toSummaryDTOList(tasks);
    }

    public TaskResponseDTO getTaskById(Integer id) {
        User user = userService.getLoggedUser();
        Task task = requireTask(id);
        requireCanAccessTask(task, user);
        return TaskMapper.toResponseDTO(task);
    }

    public List<TaskResponseDTO> getTasksByTeam(Long teamId) {
        User user = userService.getLoggedUser();
        Team team = teamAccessAuthService.requireTeam(teamId);

        if(!teamAccessAuthService.isMember(team, user)) {
            throw new UnauthorizedActionException("You are not allowed to visualise tasks from teams you are not part");
        }

        List<Task> tasks = tasksRepository.findByTeam(team);
        return TaskMapper.toResponseDTOlist(tasks);
    }

    @Transactional
    public TaskResponseDTO updateTask(Integer id, TaskUpdateDTO dto) {
        User user = userService.getLoggedUser();
        Task task = requireTask(id);
        requireCanAccessTask(task, user);
        TaskMapper.updateEntity(task, dto);
        return TaskMapper.toResponseDTO(tasksRepository.save(task));
    }

    public List<TaskSummaryDTO> findByTitle(String keyword) {
        User user = userService.getLoggedUser();
        List<Task> tasks = tasksRepository.findByUserAndTitleContainingIgnoreCase(user, keyword);
        return TaskMapper.toSummaryDTOList(tasks);
    }

    public List<TaskSummaryDTO> findByStatus(Status status) {
        User  user = userService.getLoggedUser();
        List<Task> tasks = tasksRepository.findByUserAndStatus(user, status);
        return TaskMapper.toSummaryDTOList(tasks);
    }

    @Transactional
    public void deleteTask(Integer id) {
        User user = userService.getLoggedUser();
        Task task = requireTask(id);
        requireCanAccessTask(task, user);
        tasksRepository.delete(task);
    }

    // Helpers
    private void requireCanAccessTask(Task task, User user) {
        if(task.getTeam() != null) {
            Team team = task.getTeam();
            TeamMembership membership = teamAccessAuthService.requireMembership(team, user);

            // Members can only access tasks that belong to them (if that’s your rule)
            if(membership.getTeamRole() == TeamRole.MEMBER && !task.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedActionException("You are not allowed to perform this action");
            }
        }

        if(!task.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedActionException("You cannot update this personal task");
        }
    }

    private Task requireTask(Integer id) {
        return tasksRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }
}
