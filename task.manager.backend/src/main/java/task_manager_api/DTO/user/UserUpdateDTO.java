package task_manager_api.DTO.user;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import task_manager_api.model.UserTitle;
import task_manager_api.validation.NewPasswordMatches;

@Getter
@Setter
@NewPasswordMatches
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
