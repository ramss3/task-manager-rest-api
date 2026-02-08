package task_manager_api.DTO.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamSummaryDTO {

    private Long id;

    private String teamName;

    private LocalDateTime createdAt;
}
