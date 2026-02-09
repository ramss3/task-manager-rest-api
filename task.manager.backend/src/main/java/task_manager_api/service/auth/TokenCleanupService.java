package task_manager_api.service.auth;

import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import task_manager_api.repository.VerificationTokenRepository;

@Service
public class TokenCleanupService {

    private final VerificationTokenRepository verificationTokenRepository;

    public TokenCleanupService(VerificationTokenRepository verificationTokenRepository) {
        this.verificationTokenRepository = verificationTokenRepository;
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void deleteExpiredTokens() {
        verificationTokenRepository.deleteExpiredTokens();
    }
}
