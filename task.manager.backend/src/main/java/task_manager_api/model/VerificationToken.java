package task_manager_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String token;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id", unique = true)
    private User user;

    private LocalDateTime verificationExpiryDate;

    // Created for Hibernate, since it needs a no-arguments constructor to
    // Instantiate the entity via reflection, hydrate it from database rows
    // Create proxies if needed
    protected VerificationToken() {}

    public VerificationToken(String token, User user) {
        this.token = token;
        this.user = user;
        this.verificationExpiryDate = LocalDateTime.now().plusMinutes(5);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(verificationExpiryDate);
    }

}
