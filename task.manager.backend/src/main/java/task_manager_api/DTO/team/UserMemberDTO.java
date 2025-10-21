package task_manager_api.DTO.team;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import task_manager_api.model.TeamRole;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserMemberDTO {

    private Long id;

    private String username;

    private String email;

    private TeamRole role;
}
