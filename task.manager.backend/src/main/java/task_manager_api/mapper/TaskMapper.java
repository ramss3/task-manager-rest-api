package task_manager_api.mapper;

import task_manager_api.DTO.task.TaskCreateDTO;
import task_manager_api.DTO.task.TaskResponseDTO;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.task.TaskUpdateDTO;
import task_manager_api.DTO.team.TeamSummaryDTO;
import task_manager_api.DTO.user.UserSummaryDTO;
import task_manager_api.model.Task;
import task_manager_api.model.Team;
import task_manager_api.model.User;

import java.util.List;
import java.util.stream.Collectors;

public class TaskMapper {

    public static Task toEntity(TaskCreateDTO dto, User user, Team team) {
        Task task = new Task();
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setUser(user);
        task.setTeam(team);
        return task;
    }

    public static void updateEntity(Task task, TaskUpdateDTO dto) {
        if(dto.getTitle() != null) task.setTitle(dto.getTitle());
        if(dto.getDescription() != null) task.setDescription(dto.getDescription());
        if(dto.getStatus() != null) task.setStatus(dto.getStatus());
        if(dto.getDeadline() != null) task.setDeadline(dto.getDeadline());
    }

    public static TaskResponseDTO toResponseDTO(Task task) {
        TaskResponseDTO dto = new TaskResponseDTO();
        dto.setId(task.getId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setCreatedAt(task.getDateCreated());
        dto.setDeadline(task.getDeadline());

        if(task.getUser() != null) {
            UserSummaryDTO creator = new  UserSummaryDTO();
            creator.setUserId(task.getUser().getId());
            creator.setUsername(task.getUser().getUsername());
            creator.setEmail(task.getUser().getEmail());
            dto.setCreator(creator);
        }

        if (task.getTeam() != null) {
            TeamSummaryDTO teamDTO = new TeamSummaryDTO();
            teamDTO.setId(task.getTeam().getId());
            teamDTO.setTeamName(task.getTeam().getName());
            dto.setTeam(teamDTO);
        }

        return dto;
    }

    public static TaskSummaryDTO toSummaryDTO(Task task) {
        TaskSummaryDTO dto = new TaskSummaryDTO();
        dto.setId(task.getTeamId());
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setStatus(task.getStatus());
        dto.setDeadline(task.getDeadline());
        return dto;
    }

    public static List<TaskResponseDTO> toResponseDTOlist(List<Task> tasks) {
        return tasks.stream().map(TaskMapper::toResponseDTO).collect(Collectors.toList());
    }

    public static List<TaskSummaryDTO> toSummaryDTOList(List<Task> tasks) {
        return tasks.stream().map(TaskMapper::toSummaryDTO).collect(Collectors.toList());
    }

}
