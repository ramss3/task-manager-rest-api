package task_manager_api.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import task_manager_api.DTO.Auth.LoginRequest;
import task_manager_api.DTO.Auth.RegisterRequest;
import task_manager_api.exceptions.BadRequestException;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.RefreshToken;
import task_manager_api.model.User;
import task_manager_api.model.VerificationToken;
import task_manager_api.repository.RefreshTokenRepository;
import task_manager_api.repository.UserRepository;
import task_manager_api.repository.VerificationTokenRepository;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.notification.EmailService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    @Transactional
    public void register(RegisterRequest request, String url) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();

        if (username.isBlank()) throw new BadRequestException("Username is required");
        if (email.isBlank()) throw new BadRequestException("Email is required");

        if (userRepository.findByUsername(username).isPresent()) {
            throw new ConflictException("Username already exists");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already exists");
        }

        User user = new User();
        user.setTitle(request.getTitle());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(email);
        user.setVerified(false);
        userRepository.save(user);

        verificationTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(token, user);
        verificationTokenRepository.save(verificationToken);

        String link = url + "/api/auth/verify?token=" + token;
        emailService.sendVerificationEmail(user.getEmail(), link);
    }

    @Transactional
    public Map<String, String> login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Username not found"));

        if (!user.isVerified()) {
            throw new UnauthorizedActionException("Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedActionException("Invalid credentials");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setJti(jwtTokenProvider.getJti(refreshToken));
        rt.setTokenHash(sha256Hex(accessToken));
        rt.setExpiresAt(jwtTokenProvider.getExpiration(refreshToken).toInstant());
        refreshTokenRepository.save(rt);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }

    @Transactional
    public Map<String, String> refresh(String refreshToken) {
        if(refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }

        if(!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedActionException("Invalid refresh token");
        }

        String typ = jwtTokenProvider.getTokenType(refreshToken);
        if(!"refresh".equals(typ)) {
            throw new UnauthorizedActionException("Invalid refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String jti = jwtTokenProvider.getJti(refreshToken);

        RefreshToken stored = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new UnauthorizedActionException("Refresh token not recognized"));

        String hash = sha256Hex(refreshToken);
        if(!stored.getTokenHash().equals(hash)) {
            throw new UnauthorizedActionException("Refresh token mismatch");
        }

        if(stored.isRevoked() || stored.isExpired()) {
            throw new UnauthorizedActionException("Refresh token revoked or expired");
        }

        // Rotation -> revoke old refresh, issue and save new refresh
        stored.setRevokedAt(Instant.now());

        String newAccess = jwtTokenProvider.generateAccessToken(userId);
        String newRefresh = jwtTokenProvider.generateRefreshToken(userId);

        stored.setReplacedByJti(jwtTokenProvider.getJti(newRefresh));
        refreshTokenRepository.save(stored);

        RefreshToken newReplacement = new RefreshToken();
        newReplacement.setUserId(userId);
        newReplacement.setJti(jwtTokenProvider.getJti(newRefresh));
        newReplacement.setTokenHash(sha256Hex(refreshToken));
        newReplacement.setExpiresAt(jwtTokenProvider.getExpiration(refreshToken).toInstant());
        refreshTokenRepository.save(newReplacement);

        return Map.of(
                "accessToken", newAccess,
                "refreshToken", newRefresh
        );

    }

    @Transactional
    public void verifyAccount(String token) {
        String value = token == null ? "" : token.trim();
        if (value.isBlank()) throw new BadRequestException("Verification token is required");

        VerificationToken verificationToken = verificationTokenRepository.findByToken(value)
                .orElseThrow(() -> new ResourceNotFoundException("Verification token not found!"));

        if(verificationToken.isExpired()) {
            verificationTokenRepository.delete(verificationToken);
            throw new BadRequestException("Verification token is expired!");
        }

        User user = verificationToken.getUser();
        if (user.isVerified()) {
            verificationTokenRepository.delete(verificationToken); // token no longer needed
            throw new BadRequestException("User is already verified");
        }

        user.setVerified(true);
        userRepository.save(user);
        verificationTokenRepository.delete(verificationToken);
    }

    @Transactional
    public void resendVerificationEmail(String email, String url) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (normalized.isBlank()) throw new BadRequestException("Email is required");

        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        if(user.isVerified()) {
            throw new BadRequestException("User is already verified!");
        }

        verificationTokenRepository.deleteByUser(user);

        String newToken = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(newToken, user);
        verificationTokenRepository.save(verificationToken);

        String link = url + "/api/auth/verify?token=" + newToken;
        emailService.sendVerificationEmail(user.getEmail(), link);

        System.out.println("Resent verification email to " + user.getEmail());

    }
}
