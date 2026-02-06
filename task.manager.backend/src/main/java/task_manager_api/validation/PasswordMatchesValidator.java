package task_manager_api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {

    private String passwordField;
    private String confirmPasswordField;
    private String message;

    @Override
    public void initialize(PasswordMatches annotation) {
        this.passwordField = annotation.password();
        this.confirmPasswordField = annotation.confirmPassword();
        this.message = annotation.message();
    }
    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext context) {
        if(dto == null) return true;

        Object password = getProperty(dto, passwordField);
        Object confirm = getProperty(dto, confirmPasswordField);

        if (password == null || confirm == null) return true;

        boolean matches = password.equals(confirm);

        if(!matches) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(confirmPasswordField)
                    .addConstraintViolation();
        }
        return matches;
    }

    private Object getProperty(Object bean, String name) {
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
                if (pd.getName().equals(name) && pd.getReadMethod() != null) {
                    return pd.getReadMethod().invoke(bean);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
