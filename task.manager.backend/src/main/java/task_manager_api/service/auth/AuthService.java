package task_manager_api.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import task_manager_api.service.notification.EmailService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;

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

    public String login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Username not found"));

        if (!user.isVerified()) {
            throw new UnauthorizedActionException("Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedActionException("Invalid credentials");
        }

        return jwtTokenProvider.generateToken(user.getId());
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
