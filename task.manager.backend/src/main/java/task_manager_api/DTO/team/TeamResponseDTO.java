package task_manager_api.DTO.team;

import lombok.*;
import task_manager_api.DTO.task.TaskSummaryDTO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TeamResponseDTO {

    private Long teamId;

    private String teamName;

    private LocalDateTime createdAt;

    private List<UserMemberDTO> members = new ArrayList<>();

    private List<TaskSummaryDTO> tasks = new ArrayList<>();

}
