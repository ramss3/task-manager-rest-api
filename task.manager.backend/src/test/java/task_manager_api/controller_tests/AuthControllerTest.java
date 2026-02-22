package task_manager_api.controller_tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.DTO.Auth.LoginRequest;
import task_manager_api.DTO.Auth.RegisterRequest;
import task_manager_api.DTO.Auth.ResendVerificationRequest;
import task_manager_api.controller.AuthController;
import task_manager_api.security.JwtAuthenticationFilter;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.auth.AuthService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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

    //Register Validation
    @Test
    void register_Returns201_WhenValid() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("user");
        req.setPassword("pass");
        req.setEmail("user@email.com");
        req.setFirstName("First");
        req.setLastName("Last");
        doNothing().when(authService).register(any(RegisterRequest.class), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().string("User registered successfully! Please verify your email."));

        verify(authService).register(any(RegisterRequest.class), anyString());
    }

    @Test
    void register_Returns400_WhenMissingRequiredFields() throws Exception {
        // username/password missing -> trigger validation if controller has @Valid
        String json = """
            { "email": "rafa@email.com" }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class), anyString());
    }

    //Login Validation
    @Test
    void login_Returns400_WhenBodyEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_Returns400_WhenUsernameBlank() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("   ");
        req.setPassword("pass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_Returns400_WhenPasswordBlank() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("   ");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void login_Returns200_WhenValid() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("pass");

        when(authService.login(any())).thenReturn(Map.of(
                "accessToken", "access-jwt",
                "refreshToken", "refresh-jwt"
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"));

        verify(authService).login(any());
    }

    @Test
    void refresh_Returns200_WhenValid() throws Exception {
        when(authService.refresh(anyString())).thenReturn(Map.of(
                "accessToken", "new-access",
                "refreshToken", "new-refresh"
        ));

        String json = """
        { "refreshToken": "old-refresh" }
        """;

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));

        verify(authService).refresh("old-refresh");
    }

    @Test
    void refresh_Returns400_WhenMissingRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refresh(anyString());
    }

    //Verify Validation
    @Test
    void verifyAccount_Returns200() throws Exception {
        doNothing().when(authService).verifyAccount("abc123");

        mockMvc.perform(get("/api/auth/verify")
                        .param("token", "abc123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Account verified successfully!"));

        verify(authService).verifyAccount("abc123");
    }

    //Resend Validation
    @Test
    void resendVerification_Returns200() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("rafa@email.com");

        doNothing().when(authService).resendVerificationEmail(eq("rafa@email.com"), anyString());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Verification email sent!"));

        verify(authService).resendVerificationEmail(eq("rafa@email.com"), anyString());
    }

    @Test
    void resendVerification_Returns400_WhenEmailMissing() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerification_Returns400_WhenEmailInvalid() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resendVerificationEmail(anyString(), anyString());
    }
}
