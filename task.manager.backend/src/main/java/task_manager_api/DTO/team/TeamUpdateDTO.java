package task_manager_api.DTO.team;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
public class TeamUpdateDTO {
    @NotBlank
    private String teamName; }
