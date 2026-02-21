package task_manager_api.service_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import task_manager_api.authentication.LoginRequest;
import task_manager_api.authentication.RegisterRequest;
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
import task_manager_api.repository.RefreshTokenRepository;
import task_manager_api.model.RefreshToken;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
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
    @MockitoBean RefreshTokenRepository refreshTokenRepository;

    @Autowired
    AuthService authService;

    private RegisterRequest validRegister;

    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setup() {
        validRegister = new RegisterRequest();
        validRegister.setUsername("  User  ");
        validRegister.setEmail("  Test@Email.com  ");
        validRegister.setPassword("pass");
        validRegister.setFirstName("A");
        validRegister.setLastName("B");
    }

    private RefreshToken storedRt(long userId, String jti, boolean revoked, Instant expiresAt, String tokenHash) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setJti(jti);
        rt.setTokenHash(tokenHash);
        rt.setExpiresAt(expiresAt);
        if (revoked) rt.setRevokedAt(Instant.now());
        return rt;
    }

    private static String sha256HexForTest(String value) throws Exception {
        var md = java.security.MessageDigest.getInstance("SHA-256");
        var digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    @Test
    void register_Success_TrimsAndLowercases_AndSendsEmail() {
        when(userRepository.findByUsername("User")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("ENC");

        // Save returns same user with an ID set (so token/user relations are consistent)
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        authService.register(validRegister, BASE_URL);

        // user saved with normalized values
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User u = userCaptor.getValue();
        assertEquals("User", u.getUsername());
        assertEquals("test@email.com", u.getEmail());
        assertEquals("ENC", u.getPassword());
        assertFalse(u.isVerified());

        verify(verificationTokenRepository).deleteByUser(u);

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
        assertTrue(linkCaptor.getValue().startsWith(BASE_URL + "/api/auth/verify?token="));
    }

    @Test
    void register_Fails_WhenUsernameExists() {
        when(userRepository.findByUsername("User")).thenReturn(Optional.of(new User()));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> authService.register(validRegister, BASE_URL)
        );
        assertEquals("Username already exists", ex.getMessage());

        verify(userRepository, never()).save(any());
        verify(verificationTokenRepository, never()).deleteByUser(any());
        verify(verificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void register_Fails_WhenEmailExists() {
        when(userRepository.findByUsername("User")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(new User()));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> authService.register(validRegister, BASE_URL)
        );
        assertEquals("Email already exists", ex.getMessage());

        verify(userRepository, never()).save(any());
        verify(verificationTokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void register_Fails_WhenUsernameBlank() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("   ");
        request.setEmail("test@email.com");
        request.setPassword("pass");

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.register(request, BASE_URL)
        );
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

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.register(req, BASE_URL)
        );
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

        UnauthorizedActionException ex =
                assertThrows(UnauthorizedActionException.class, () -> authService.login(req));
        assertEquals("Please verify your email before logging in.", ex.getMessage());

        verify(refreshTokenRepository, never()).save(any());
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
    void login_Success_ReturnsAccessAndRefresh_AndSavesRefreshToken() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user");
        req.setPassword("pass");

        User u = new User();
        u.setId(5L);
        u.setVerified(true);
        u.setPassword("ENC");

        when(userRepository.findByUsername("user")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", "ENC")).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(5L)).thenReturn("ACCESS");
        when(jwtTokenProvider.generateRefreshToken(5L)).thenReturn("REFRESH");
        when(jwtTokenProvider.getJti("REFRESH")).thenReturn("jti-123");
        when(jwtTokenProvider.getExpiration("REFRESH")).thenReturn(new Date(System.currentTimeMillis() + 100000));

        Map<String, String> tokens = authService.login(req);

        assertEquals("ACCESS", tokens.get("accessToken"));
        assertEquals("REFRESH", tokens.get("refreshToken"));

        ArgumentCaptor<RefreshToken> rtCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(rtCaptor.capture());

        RefreshToken saved = rtCaptor.getValue();
        assertEquals(5L, saved.getUserId());
        assertEquals("jti-123", saved.getJti());
        assertNotNull(saved.getTokenHash());
        assertNotNull(saved.getExpiresAt());
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

        when(jwtTokenProvider.generateAccessToken(5L)).thenReturn("ACCESS");
        when(jwtTokenProvider.generateRefreshToken(5L)).thenReturn("REFRESH");
        when(jwtTokenProvider.getJti("REFRESH")).thenReturn("jti-123");
        when(jwtTokenProvider.getExpiration("REFRESH")).thenReturn(new Date(System.currentTimeMillis() + 100000));

        Map<String, String> tokens = authService.login(req);

        assertEquals("ACCESS", tokens.get("accessToken"));
        assertEquals("REFRESH", tokens.get("refreshToken"));

        verify(userRepository).findByUsername("user");
    }

    // --- Refresh Tests ---
    @Test
    void refresh_Success_RotatesAndReturnsNewTokens() throws Exception {
        String incoming = "R";
        long userId = 5L;
        String oldJti = "jti-old";

        when(jwtTokenProvider.validateToken(incoming)).thenReturn(true);
        when(jwtTokenProvider.getTokenType(incoming)).thenReturn("refresh");
        when(jwtTokenProvider.getUserIdFromToken(incoming)).thenReturn(userId);
        when(jwtTokenProvider.getJti(incoming)).thenReturn(oldJti);

        String hash = sha256HexForTest(incoming);
        RefreshToken stored = storedRt(userId, oldJti, false, Instant.now().plusSeconds(3600), hash);
        when(refreshTokenRepository.findByJti(oldJti)).thenReturn(Optional.of(stored));

        when(jwtTokenProvider.generateAccessToken(userId)).thenReturn("NEW_ACCESS");
        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn("NEW_REFRESH");
        when(jwtTokenProvider.getJti("NEW_REFRESH")).thenReturn("jti-new");

        when(jwtTokenProvider.getExpiration(anyString()))
                .thenReturn(new Date(System.currentTimeMillis() + 100000));

        Map<String, String> out = authService.refresh(incoming);

        assertEquals("NEW_ACCESS", out.get("accessToken"));
        assertEquals("NEW_REFRESH", out.get("refreshToken"));

        assertNotNull(stored.getRevokedAt());
        assertEquals("jti-new", stored.getReplacedByJti());

        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_Fails_WhenTokenBlank() {
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.refresh("   ")
        );
        assertEquals("Refresh token is required", ex.getMessage());

        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void refresh_Fails_WhenJwtInvalid() {
        when(jwtTokenProvider.validateToken("BAD")).thenReturn(false);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> authService.refresh("BAD")
        );
        assertEquals("Invalid refresh token", ex.getMessage());

        verify(jwtTokenProvider).validateToken("BAD");
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void refresh_Fails_WhenNotRecognizedInDb() {
        when(jwtTokenProvider.validateToken("R")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("R")).thenReturn("refresh");
        when(jwtTokenProvider.getUserIdFromToken("R")).thenReturn(5L);
        when(jwtTokenProvider.getJti("R")).thenReturn("jti-old");

        when(refreshTokenRepository.findByJti("jti-old")).thenReturn(Optional.empty());

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> authService.refresh("R")
        );
        assertEquals("Refresh token not recognized", ex.getMessage());
    }

    @Test
    void refresh_Fails_WhenHashMismatch() {
        when(jwtTokenProvider.validateToken("R")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("R")).thenReturn("refresh");
        when(jwtTokenProvider.getUserIdFromToken("R")).thenReturn(5L);
        when(jwtTokenProvider.getJti("R")).thenReturn("jti-old");

        // stored hash doesn't match what service will compute for R
        RefreshToken stored = storedRt(
                5L,
                "jti-old",
                false,
                Instant.now().plusSeconds(3600),
                "some-other-hash"
        );
        when(refreshTokenRepository.findByJti("jti-old")).thenReturn(Optional.of(stored));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> authService.refresh("R")
        );
        assertEquals("Refresh token mismatch", ex.getMessage());
    }

    @Test
    void refresh_Fails_WhenStoredRevoked() throws Exception {
        when(jwtTokenProvider.validateToken("R")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("R")).thenReturn("refresh");
        when(jwtTokenProvider.getUserIdFromToken("R")).thenReturn(5L);
        when(jwtTokenProvider.getJti("R")).thenReturn("jti-old");

        String hash = sha256HexForTest("R");
        RefreshToken stored = storedRt(5L, "jti-old", true, Instant.now().plusSeconds(3600), hash);
        when(refreshTokenRepository.findByJti("jti-old")).thenReturn(Optional.of(stored));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> authService.refresh("R")
        );
        assertEquals("Refresh token revoked or expired", ex.getMessage());
    }

    @Test
    void refresh_Fails_WhenStoredExpired() throws Exception {
        when(jwtTokenProvider.validateToken("R")).thenReturn(true);
        when(jwtTokenProvider.getTokenType("R")).thenReturn("refresh");
        when(jwtTokenProvider.getUserIdFromToken("R")).thenReturn(5L);
        when(jwtTokenProvider.getJti("R")).thenReturn("jti-old");

        String hash = sha256HexForTest("R");
        RefreshToken stored = storedRt(5L, "jti-old", false, Instant.now().minusSeconds(1), hash);
        when(refreshTokenRepository.findByJti("jti-old")).thenReturn(Optional.of(stored));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> authService.refresh("R")
        );
        assertEquals("Refresh token revoked or expired", ex.getMessage());
    }

    // --- Verify Tests ---
    @Test
    void verifyAccount_Fails_WhenTokenBlank() {
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.verifyAccount("   ")
        );
        assertEquals("Verification token is required", ex.getMessage());

        verify(verificationTokenRepository, never()).findByToken(anyString());
        verify(verificationTokenRepository, never()).delete(any(VerificationToken.class));
        verify(userRepository, never()).save(any(User.class));
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
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.resendVerificationEmail("   ", BASE_URL)
        );
        assertEquals("Email is required", ex.getMessage());

        // the method should fail before touching persistence or email
        verify(userRepository, never()).findByEmail(anyString());
        verify(verificationTokenRepository, never()).deleteByUser(any(User.class));
        verify(verificationTokenRepository, never()).save(any(VerificationToken.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationEmail_Fails_WhenUserNotFound() {
        when(userRepository.findByEmail("x@email.com")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> authService.resendVerificationEmail("X@Email.com", BASE_URL));
        assertEquals("User not found!", ex.getMessage());
    }

    @Test
    void resendVerificationEmail_Fails_WhenUserAlreadyVerified() {
        User user = new User();
        user.setEmail("test@email.com");
        user.setVerified(true);

        when(userRepository.findByEmail("test@email.com")).thenReturn(Optional.of(user));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.resendVerificationEmail("Test@Email.com", BASE_URL));
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

        authService.resendVerificationEmail("  Test@Email.com  ", BASE_URL);

        verify(verificationTokenRepository).deleteByUser(user);
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("test@email.com"),
                startsWith(BASE_URL + "/api/auth/verify?token="));
    }
}

