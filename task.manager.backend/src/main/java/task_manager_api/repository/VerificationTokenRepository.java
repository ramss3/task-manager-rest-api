package task_manager_api.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import task_manager_api.model.VerificationToken;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String verificationToken);

    @Modifying
    @Transactional
    @Query("DELETE FROM VerificationToken t WHERE t.verificationExpiryDate < CURRENT_TIMESTAMP OR t.used = true")
    void deleteExpiredTokens();
}
