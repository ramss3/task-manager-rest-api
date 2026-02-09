package task_manager_api.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.user.UserCreateDTO;
import task_manager_api.DTO.user.UserResponseDTO;
import task_manager_api.DTO.user.UserUpdateDTO;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.mapper.TeamMapper;
import task_manager_api.mapper.UserMapper;
import task_manager_api.model.User;
import task_manager_api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import task_manager_api.security.UserPrincipal;

import java.util.List;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLookupService userLookupService;

    // --- Create ---
    @Transactional
    public UserResponseDTO createUser(UserCreateDTO dto) {
        try {
            User user = new User();
            user.setTitle(dto.getUserTitle());
            user.setUsername(dto.getUsername().trim());
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            user.setFirstName(dto.getFirstName());
            user.setLastName(dto.getLastName());
            user.setEmail(dto.getEmail().trim().toLowerCase());
            return UserMapper.toResponseDTO(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Username or email already exists");
        }
    }

    // --- Read ---
    public User getLoggedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {

            return userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Logged user not found"));
        }
        throw new UnauthorizedActionException("User is not authenticated");
    }

    public UserResponseDTO getUserByUsername(String username) {
        return UserMapper.toResponseDTO(
                userRepository.findByUsername(username.trim())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"))
        );
    }

    public  UserResponseDTO getUserByEmail(String email) {
        return UserMapper.toResponseDTO(
                userRepository.findByEmail(email.trim().toLowerCase())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"))
        );
    }

    public UserResponseDTO getUserById(Long userId) {
        return UserMapper.toResponseDTO(userLookupService.requireUser(userId));
    }

    @Transactional(readOnly = true)
    public List<TeamResponseDTO> getMyTeams() {
        return getLoggedUser().getTeams()
                .stream()
                .map(TeamMapper::toResponseDTO)
                .toList();
    }

    // --- Update ---
    @Transactional
    public UserResponseDTO updateUser(Long id, UserUpdateDTO dto) {
        User logged = getLoggedUser();
        if (!logged.getId().equals(id)) {
            throw new UnauthorizedActionException("You cannot update another user");
        }
        User user = userLookupService.requireUser(id);

        if (dto.getUserTitle() != null) user.setTitle(dto.getUserTitle());
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getUsername() != null) {
            String newUsername = dto.getUsername().trim();
            if(userRepository.existsByUsernameAndIdNot(newUsername, user.getId())) {
                throw new ConflictException("Username is already taken");
            }
            user.setUsername(newUsername);
        }

        if (dto.getEmail() != null) {
            String newEmail = dto.getEmail().trim().toLowerCase();
            if(userRepository.existsByEmailAndIdNot(newEmail, user.getId())) {
                throw new ConflictException("The new emails already exists");
            }
            user.setEmail(newEmail);
        }

        boolean hasCurr = dto.getCurrentPassword() != null && !dto.getCurrentPassword().isBlank();
        boolean hasNew = dto.getNewPassword() != null && !dto.getNewPassword().isBlank();

        if(hasCurr ^ hasNew) {
            throw new ConflictException("To change the password, provide current and new password");
        }

        if (hasCurr) {
            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new UnauthorizedActionException("Current password does not match");
            }
            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new UnauthorizedActionException("New password is the same as the old one");
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        try {
            return UserMapper.toResponseDTO(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Username or email already exists");
        }
    }

    // --- Delete ---
    @Transactional
    public void deleteUser(Long id) {
        User loggedUser = getLoggedUser();

        if (!loggedUser.getId().equals(id)) {
            throw new UnauthorizedActionException("You are not allowed to delete another user's account");
        }

        userRepository.delete(loggedUser);
    }
}
