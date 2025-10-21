package task_manager_api.mapper;

import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.model.Team;
import task_manager_api.model.TeamMembership;
import task_manager_api.model.TeamRole;

import java.util.List;
import java.util.stream.Collectors;

public class TeamMapper {

    public static TeamResponseDTO toResponseDTO(Team team) {
        if (team == null) return null;
        TeamResponseDTO dto = new TeamResponseDTO();
        dto.setTeamName(team.getName());
        dto.setTeamId(team.getId());
        dto.setCreatedAt(team.getCreatedAt());

        List<UserMemberDTO> members = team.getMemberships() != null
                ? team.getMemberships().stream()
                    .map(m -> UserMapper.toMemberDTO(m.getUser(), m.getTeamRole()))
                    .toList()
                : List.of();
        dto.setMembers(members);

        List<TaskSummaryDTO> tasks = team.getTeamTasks() != null
                ? team.getTeamTasks().stream()
                    .map(TaskMapper::toSummaryDTO)
                    .toList()
                : List.of();
        dto.setTasks(tasks);

        return dto;
    }
}
