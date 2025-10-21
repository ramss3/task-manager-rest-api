package task_manager_api.DTO.task;

import lombok.*;
import task_manager_api.model.Status;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskSummaryDTO {

    private Long id;

    private String title;

    private String description;

    private Status status;

    private LocalDateTime deadline;

}
