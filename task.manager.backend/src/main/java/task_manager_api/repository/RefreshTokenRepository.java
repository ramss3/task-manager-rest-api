package task_manager_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import task_manager_api.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByJti(String jti);
    void deleteByUserId(Long userId);
}
