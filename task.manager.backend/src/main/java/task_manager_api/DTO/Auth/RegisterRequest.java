package task_manager_api.DTO.Auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import task_manager_api.model.UserTitle;

@Getter
@Setter
public class RegisterRequest {

    private UserTitle title;

    private String firstName;

    private String lastName;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @Email
    @NotBlank
    private String email;
}
