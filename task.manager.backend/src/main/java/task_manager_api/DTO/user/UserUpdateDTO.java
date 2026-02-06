package task_manager_api.DTO.user;

import lombok.*;
import task_manager_api.model.UserTitle;
import task_manager_api.validation.PasswordMatches;

@Getter
@Setter
@PasswordMatches(
        password = "newPassword",
        confirmPassword = "confirmNewPassword",
        message = "New password and confirmation do not match"
)
public class UserUpdateDTO {

    private UserTitle userTitle;

    private String firstName;

    private String lastName;

    private String username;

    private String currentPassword;

    private String newPassword;

    private String confirmNewPassword;

    private String email;
}
