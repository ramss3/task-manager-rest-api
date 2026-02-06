package task_manager_api.DTO.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import task_manager_api.model.UserTitle;
import task_manager_api.validation.PasswordMatches;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@PasswordMatches(
        password = "password",
        confirmPassword = "confirmPassword"
)
public class UserCreateDTO {

    private UserTitle userTitle;

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String confirmPassword;

    private String firstName;

    private String lastName;

    @NotBlank
    @Email
    private String email;

}
