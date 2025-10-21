package task_manager_api.DTO.user;

import lombok.*;
import task_manager_api.model.UserTitle;

@Getter
@Setter
public class UserResponseDTO {

    private Long id;

    private UserTitle userTitle;

    private String firstName;

    private String lastName;

    private String username;

    private String email;
}
