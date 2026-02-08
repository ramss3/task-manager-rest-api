package task_manager_api.DTO.team;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddTeamMemberDTO {

    @NotBlank(message = "Introduce a username or an email")
    private String identifier;

}
