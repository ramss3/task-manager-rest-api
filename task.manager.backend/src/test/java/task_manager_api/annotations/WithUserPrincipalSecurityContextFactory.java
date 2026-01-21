package task_manager_api.annotations;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import task_manager_api.model.User;
import task_manager_api.security.UserPrincipal;

public class WithUserPrincipalSecurityContextFactory
        implements WithSecurityContextFactory<WithUserPrincipal> {

    @Override
    public SecurityContext createSecurityContext(WithUserPrincipal annotation) {

        User user = new User();
        user.setId(annotation.id());
        user.setUsername(annotation.username());
        user.setVerified(true);

        UserPrincipal principal = new UserPrincipal(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
