package task_manager_api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import task_manager_api.DTO.user.UserCreateDTO;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, UserCreateDTO> {

    @Override
    public boolean isValid(UserCreateDTO dto, ConstraintValidatorContext context) {
        if(dto == null) return true;
        if(dto.getPassword() == null || dto.getConfirmPassword() == null) return true;

        boolean matches = dto.getPassword().equals(dto.getConfirmPassword());

        if(!matches) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Passwords do not match")
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }
        return matches;
    }
}
