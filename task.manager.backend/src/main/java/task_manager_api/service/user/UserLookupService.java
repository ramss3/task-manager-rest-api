package task_manager_api.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.model.User;
import task_manager_api.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserRepository userRepository;

    public User searchByIdentifier(String identifier) {
        String input = identifier == null ? "" : identifier.trim();

        if(input.isBlank()) throw new ConflictException("identifier cannot be empty");

        return (input.contains("@")
            ? userRepository.findByEmail(input)
            : userRepository.findByUsername(input))
                .orElseThrow(() -> new ResourceNotFoundException("User not found. User either does not exist or wrong identifier inserted."
                ));
    }

    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
