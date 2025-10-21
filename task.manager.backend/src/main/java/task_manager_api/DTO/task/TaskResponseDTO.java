package task_manager_api.DTO.task;

import lombok.*;
import task_manager_api.DTO.team.TeamSummaryDTO;
import task_manager_api.DTO.user.UserSummaryDTO;
import task_manager_api.model.Status;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponseDTO {

    private Integer id;

    private String title;

    private String description;

    private Status status;

    private LocalDateTime createdAt;

    private LocalDateTime deadline;

    private UserSummaryDTO creator;

    private TeamSummaryDTO team;
}
