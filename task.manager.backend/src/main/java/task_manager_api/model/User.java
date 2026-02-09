package task_manager_api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private UserTitle title;

    private String firstName;

    private String lastName;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    @NotBlank
    private String password;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean verified = false;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL,  orphanRemoval = true)
    private List<TeamMembership> userTeams = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public List<Team> getTeams() {
        return userTeams.stream()
                .map(TeamMembership::getTeam)
                .toList();
    }
}

