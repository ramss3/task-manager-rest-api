package task_manager_api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordMatchesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMatches {

    String password();
    String confirmPassword();
    String message() default "Passwords do not match";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
