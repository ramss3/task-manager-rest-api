package task_manager_api.model;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class TeamMembershipId implements Serializable {
    private Long userId;
    private Long teamId;
}
