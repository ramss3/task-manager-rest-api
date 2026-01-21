package task_manager_api.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import task_manager_api.DTO.authentication.LoginRequest;
import task_manager_api.DTO.authentication.RegisterRequest;
import task_manager_api.exceptions.BadRequestException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.model.User;
import task_manager_api.model.VerificationToken;
import task_manager_api.repository.UserRepository;
import task_manager_api.repository.VerificationTokenRepository;
import task_manager_api.security.JwtTokenProvider;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;


    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       EmailService emailService, VerificationTokenRepository verificationTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.verificationTokenRepository = verificationTokenRepository;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("The Username you set already exists!");
        }

        User user = new User();
        user.setTitle(request.getTitle());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setVerified(false);
        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(token, user);
        verificationTokenRepository.save(verificationToken);

        String link = "http://localhost:8080/api/auth/verify?token=" + token;
        emailService.sendVerificationEmail(user.getEmail(), token, link);


    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow(() -> new RuntimeException("Username not found!"));

        if (!user.isVerified()) {
            throw new RuntimeException("Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        return jwtTokenProvider.generateToken(user.getId());
    }

    public void verifyAccount(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Verification token not found!"));

        if(verificationToken.isExpired()) {
            throw new RuntimeException("Verification token is expired!");
        }

        User user = verificationToken.getUser();
        user.setVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.delete(verificationToken);

        verificationTokenRepository.delete(verificationToken);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        if(user.isVerified()) {
            throw new BadRequestException("User is already verified!");
        }

        verificationTokenRepository.findAll().stream()
                .filter(token -> token.getUser().equals(user))
                .forEach(verificationTokenRepository::delete);

        String newToken = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken(newToken, user);
        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        String link = "http://localhost:8080/api/auth/verify?token=" + newToken;
        emailService.sendVerificationEmail(user.getEmail(), newToken, link);

        System.out.println("Resent verification email to " + user.getEmail());

    }


}
