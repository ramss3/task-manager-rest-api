package task_manager_api.service_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import task_manager_api.DTO.authentication.LoginRequest;
import task_manager_api.DTO.authentication.RegisterRequest;
import task_manager_api.exceptions.BadRequestException;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.User;
import task_manager_api.model.VerificationToken;
import task_manager_api.repository.UserRepository;
import task_manager_api.repository.VerificationTokenRepository;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.auth.AuthService;
import task_manager_api.service.notification.EmailService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AuthServiceTest {

    @MockitoBean
    UserRepository userRepository;
    @MockitoBean VerificationTokenRepository verificationTokenRepository;
    @MockitoBean
    PasswordEncoder passwordEncoder;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean EmailService emailService;

    @Autowired
    AuthService authService;

    private RegisterRequest validRegister;

    @BeforeEach
    void setup() {
        validRegister = new RegisterRequest();
        validRegister.setUsername("  User  ");
        validRegister.setEmail("  Test@Email.com  ");
        validRegister.setPassword("pass");
        validRegister.setFirstName("A");
        validRegister.setLastName("B");
    }

    @Test
    void register_Success_TrimsAndLowercases_AndSendsEmail() {
        when(userRepository.findByUsername("User")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("ENC");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        authService.register(validRegister);

        // user saved with normalized values
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User u = userCaptor.getValue();
        assertEquals("User", u.getUsername());
        assertEquals("test@email.com", u.getEmail());
        assertEquals("ENC", u.getPassword());
        assertFalse(u.isVerified());

        // capture token and ensure email link contains the SAME token
        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken vt = tokenCaptor.getValue();
        assertNotNull(vt.getToken());

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(emailCaptor.capture(), linkCaptor.capture());

        assertEquals("test@email.com", emailCaptor.getValue());
        assertTrue(linkCaptor.getValue().contains(vt.getToken()));
        assertTrue(linkCaptor.getValue().startsWith("http://localhost:8080/api/auth/verify?token="));
    }

    @Test
    void register_Fails_WhenUsernameExists() {
        when(userRepository.findByUsername("User")).thenReturn(Optional.of(new User()));

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.register(validRegister));
        assertEquals("Username already exists", ex.getMessage());

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void register_Fails_WhenEmailExists() {
        when(userRepository.findByUsername("User")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(new User()));

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.register(validRegister));
        assertEquals("Email already exists", ex.getMessage());
    }

    @Test
    void register_Fails_WhenUsernameBlank() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setEmail("test@email.com");
        request.setPassword("pass");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
        assertEquals("Username is required", ex.getMessage());

        verifyNoInteractions(emailService);
        verify(verificationTokenRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_Fails_WhenEmailBlank() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("user");
        req.setEmail("   ");
        req.setPassword("pass");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertEquals("Email is required", ex.getMessage());

        verifyNoInteractions(emailService);
        verify(verificationTokenRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    // --- Login Tests ---
    @Test
    void login_Fails_WhenNotVerified() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("pass");

        User u = new User();
        u.setVerified(false);
        u.setPassword("ENC");

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(u));

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () -> authService.login(req));
        assertEquals("Please verify your email before logging in.", ex.getMessage());
    }

    @Test
    void login_Fails_WhenPasswordWrong() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("pass");

        User u = new User();
        u.setVerified(true);
        u.setPassword("ENC");

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "ENC")).thenReturn(false);

        UnauthorizedActionException ex = assertThrows(UnauthorizedActionException.class, () -> authService.login(req));
        assertEquals("Invalid credentials", ex.getMessage());
    }

    @Test
    void login_Success_ReturnsJwt() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("pass");

        User u = new User();
        u.setId(5L);
        u.setVerified(true);
        u.setPassword("ENC");

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "ENC")).thenReturn(true);
        when(jwtTokenProvider.generateToken(5L)).thenReturn("JWT");

        String token = authService.login(req);
        assertEquals("JWT", token);
    }

    @Test
    void login_Fails_WhenUsernameNotFound() {
        LoginRequest req = new LoginRequest();
        req.setUsername("missing");
        req.setPassword("pass");

        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> authService.login(req));
        assertEquals("Username not found", ex.getMessage());
    }

    @Test
    void login_TrimsUsername_BeforeLookup() {
        LoginRequest req = new LoginRequest();
        req.setUsername("  user  ");
        req.setPassword("pass");

        User u = new User();
        u.setId(5L);
        u.setVerified(true);
        u.setPassword("ENC");

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "ENC")).thenReturn(true);
        when(jwtTokenProvider.generateToken(5L)).thenReturn("JWT");

        String token = authService.login(req);
        assertEquals("JWT", token);

        verify(userRepository).findByUsername("user");
    }

    // --- Verify Tests ---
    @Test
    void verifyAccount_Fails_WhenTokenBlank() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.verifyAccount("   "));
        assertEquals("Verification token is required", ex.getMessage());

        verifyNoInteractions(verificationTokenRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyAccount_Fails_WhenTokenNotFound() {
        when(verificationTokenRepository.findByToken("abc")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> authService.verifyAccount("abc"));
        assertEquals("Verification token not found!", ex.getMessage());
    }

    @Test
    void verifyAccount_Fails_WhenTokenExpired_DeletesToken() {
        User user = new User();
        user.setVerified(false);

        VerificationToken vt = new VerificationToken("abc", user);
        vt.setVerificationExpiryDate(LocalDateTime.now().minusMinutes(1)); // expired

        when(verificationTokenRepository.findByToken("abc")).thenReturn(Optional.of(vt));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.verifyAccount("abc"));
        assertEquals("Verification token is expired!", ex.getMessage());

        verify(verificationTokenRepository).delete(vt);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyAccount_Fails_WhenUserAlreadyVerified_DeletesToken() {
        User user = new User();
        user.setVerified(true);

        VerificationToken vt = new VerificationToken("abc", user);
        vt.setVerificationExpiryDate(LocalDateTime.now().plusMinutes(5)); // valid

        when(verificationTokenRepository.findByToken("abc")).thenReturn(Optional.of(vt));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.verifyAccount("abc"));
        assertEquals("User is already verified", ex.getMessage());

        verify(verificationTokenRepository).delete(vt);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyAccount_Success_VerifiesUser_AndDeletesToken() {
        User user = new User();
        user.setVerified(false);

        VerificationToken vt = new VerificationToken("abc", user);
        vt.setVerificationExpiryDate(LocalDateTime.now().plusMinutes(5)); // valid

        when(verificationTokenRepository.findByToken("abc")).thenReturn(Optional.of(vt));
        when(userRepository.save(user)).thenReturn(user);

        authService.verifyAccount("abc");

        assertTrue(user.isVerified());
        verify(userRepository).save(user);
        verify(verificationTokenRepository).delete(vt);
    }

    // --- Resend tests ---
    @Test
    void resendVerificationEmail_Fails_WhenEmailBlank() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.resendVerificationEmail("   "));
        assertEquals("Email is required", ex.getMessage());

        verifyNoInteractions(userRepository);
        verifyNoInteractions(verificationTokenRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerificationEmail_Fails_WhenUserNotFound() {
        when(userRepository.findByEmail("x@email.com")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> authService.resendVerificationEmail("X@Email.com"));
        assertEquals("User not found!", ex.getMessage());
    }

    @Test
    void resendVerificationEmail_Fails_WhenUserAlreadyVerified() {
        User user = new User();
        user.setEmail("test@email.com");
        user.setVerified(true);

        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(user));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.resendVerificationEmail("Test@Email.com"));
        assertEquals("User is already verified!", ex.getMessage());

        verify(verificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationEmail_Success_DeletesOldToken_SavesNew_SendsEmail() {
        User user = new User();
        user.setEmail("test@email.com");
        user.setVerified(false);

        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(user));

        authService.resendVerificationEmail("  Test@Email.com  ");

        verify(verificationTokenRepository).deleteByUser(user);
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("test@email.com"), contains("/api/auth/verify?token="));
    }
}

