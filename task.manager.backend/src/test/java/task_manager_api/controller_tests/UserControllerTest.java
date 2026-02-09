package task_manager_api.controller_tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.controller.UserController;
import task_manager_api.model.User;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.security.UserPrincipal;
import task_manager_api.service.auth.CustomUserDetailsService;
import task_manager_api.service.user.UserService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false) // Keeps the Handler from being null
@Import(UserControllerTest.SecurityTestConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    // 3. Define a mini-config to enable @PreAuthorize in the test
    @TestConfiguration
    @EnableMethodSecurity
    static class SecurityTestConfig {
    }

    @Test
    void testDeleteOwnAccount_ShouldSucceed() throws Exception {
        Long userId = 1L;
        User userEntity = new User();
        userEntity.setId(userId);
        UserPrincipal principal = new UserPrincipal(userEntity);

        // Instead of .with(user()), we manually set the context for this thread
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        try {
            mockMvc.perform(delete("/api/users/{id}", userId))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            verify(userService).deleteUser(userId);
        } finally {
            SecurityContextHolder.clearContext(); // Always clear after manual set
        }
    }

    @Test
    void testDeleteOtherAccount_ShouldBeForbidden() throws Exception {
        Long myId = 1L;
        Long targetId = 2L;

        User userEntity = new User();
        userEntity.setId(myId);
        UserPrincipal principal = new UserPrincipal(userEntity);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        try {
            // We catch the ServletException and check if the CAUSE is the Access Denied error
            jakarta.servlet.ServletException exception = assertThrows(jakarta.servlet.ServletException.class, () -> {
                mockMvc.perform(delete("/api/users/{id}", targetId));
            });

            // Verify the root cause is indeed an Authorization error
            assertTrue(exception.getCause() instanceof org.springframework.security.authorization.AuthorizationDeniedException);

            // Verify the service was NEVER called
            verify(userService, never()).deleteUser(targetId);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
