package task_manager_api.service;

import task_manager_api.DTO.task.TaskCreateDTO;
import task_manager_api.DTO.task.TaskResponseDTO;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.task.TaskUpdateDTO;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.mapper.TaskMapper;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class TaskService {

    private final TasksRepository tasksRepository;
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final UserService userService;

    public TaskService(TasksRepository tasksRepository,
                       TeamRepository teamRepository,
                       TeamMembershipRepository teamMembershipRepository,
                       UserService userService) {
        this.tasksRepository = tasksRepository;
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.userService = userService;
    }

    private void unAuthorizationActionVerificationAuthorization(Task task, User user) {
        if(task.getTeam() != null) {
            Team team = task.getTeam();
            TeamMembership membership = teamMembershipRepository.findByTeamAndUser(team, user)
                    .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this team"));

            TeamRole role = membership.getTeamRole();

            if(role == TeamRole.MEMBER && !task.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedActionException("You are not allowed to perform this action");
            }
        } else {
            if(!task.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedActionException("You cannot update this personal task");
            }
        }
    }

    public TaskResponseDTO createTask(TaskCreateDTO dto) {
        User user = userService.getLoggedUser();
        Team team = null;

        if(dto.getTeamId() != null) {
            team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

            if (!teamMembershipRepository.existsByTeamAndUser(team, user)) {
                throw new UnauthorizedActionException("You are attempting to assign a task to a team that you do not belong to");
            }
        }

        Task task = TaskMapper.toEntity(dto, user, team);
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
        Task task = tasksRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        unAuthorizationActionVerificationAuthorization(task, user);

        return TaskMapper.toResponseDTO(task);
    }

    public TaskResponseDTO updateTask(Integer id, TaskUpdateDTO dto) {

        User user = userService.getLoggedUser();
        Task task = tasksRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        unAuthorizationActionVerificationAuthorization(task, user);

        TaskMapper.updateEntity(task, dto);
        Task updated = tasksRepository.save(task);

        return TaskMapper.toResponseDTO(task);
    }

    public List<TaskSummaryDTO> findByTitle(String keyword) {
        List<Task> tasks = tasksRepository.findByTitleContainingIgnoreCase(keyword);
        return TaskMapper.toSummaryDTOList(tasks);
    }

    public List<TaskSummaryDTO> findByStatus(Status status) {
        List<Task> tasks = tasksRepository.findByStatus(status);
        return TaskMapper.toSummaryDTOList(tasks);
    }

    public void deleteTask(Integer id) {
        User user = userService.getLoggedUser();
        Task task = tasksRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        unAuthorizationActionVerificationAuthorization(task, user);

        tasksRepository.delete(task);
    }
}
