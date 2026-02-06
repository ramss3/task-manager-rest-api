package task_manager_api.DTO.task;

import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCreateDTO {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocalDateTime deadline;

    @Transient
    private Long teamId;
}
