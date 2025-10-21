package task_manager_api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import task_manager_api.DTO.user.UserUpdateDTO;

public class NewPasswordMatchesValidator implements ConstraintValidator<NewPasswordMatches, UserUpdateDTO> {

    @Override
    public boolean isValid(UserUpdateDTO dto, ConstraintValidatorContext context) {
        if(dto == null) return true;

        if(dto.getNewPassword() == null || dto.getConfirmNewPassword() == null) return true;

        boolean matches = dto.getNewPassword().equals(dto.getConfirmNewPassword());

        if(!matches) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Passwords do not match")
                    .addPropertyNode("confirmNewPassword")
                    .addConstraintViolation();
        }

        return matches;
    }
}
