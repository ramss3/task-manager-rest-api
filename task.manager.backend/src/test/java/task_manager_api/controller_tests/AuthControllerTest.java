package task_manager_api.controller_tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.DTO.authentication.ResendVerificationRequest;
import task_manager_api.controller.AuthController;
import task_manager_api.security.JwtAuthenticationFilter;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.auth.AuthService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void register_Returns201() throws Exception {
        String json = """
            {
              "username": "rafa",
              "password": "password123",
              "email": "rafa@email.com",
              "firstName": "Rafael",
              "lastName": "Silva",
              "title": "Mr"
            }
            """;

        doNothing().when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(content().string("User registered successfully! Please verify your email."));

        verify(authService).register(any());
    }

    @Test
    void register_Returns400_WhenMissingRequiredFields() throws Exception {
        // username/password missing -> should trigger validation if controller has @Valid
        String json = """
            { "email": "rafa@email.com" }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void login_Returns200_WithToken() throws Exception {
        String json = """
            { "username": "rafa", "password": "password123" }
            """;

        when(authService.login(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService).login(any());
    }

    @Test
    void verifyAccount_Returns200() throws Exception {
        doNothing().when(authService).verifyAccount("abc123");

        mockMvc.perform(get("/api/auth/verify")
                        .param("token", "abc123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account verified successfully!"));

        verify(authService).verifyAccount("abc123");
    }

    @Test
    void resendVerification_Returns200() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("rafa@email.com");

        doNothing().when(authService).resendVerificationEmail("rafa@email.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Verification email sent!"));

        verify(authService).resendVerificationEmail("rafa@email.com");
    }

    @Test
    void resendVerification_Returns400_WhenEmailMissing() throws Exception {
        // empty body => @Valid should fail because email is @NotBlank
        ResendVerificationRequest request = new ResendVerificationRequest();

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resendVerificationEmail(anyString());
    }

    @Test
    void resendVerification_Returns400_WhenEmailInvalid() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resendVerificationEmail(anyString());
    }
}
