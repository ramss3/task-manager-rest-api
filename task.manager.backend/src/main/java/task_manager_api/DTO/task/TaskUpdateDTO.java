package task_manager_api.DTO.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import task_manager_api.model.Status;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskUpdateDTO {

    private String title;

    private String description;

    private Status status;

    private LocalDateTime deadline;
}
