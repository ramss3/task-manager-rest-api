package task_manager_api.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
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

import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // --- Create ---
    public UserResponseDTO createUser(UserCreateDTO dto) {
        User user = new User();
        user.setTitle(dto.getUserTitle());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        userRepository.save(user);

        return UserMapper.toResponseDTO(user);
    }

    // --- Read ---
    public User getLoggedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {

            // FAST: Get user directly by ID
            return userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Logged user not found"));
        }

        throw new UnauthorizedActionException("User is not authenticated");
    }

    public UserResponseDTO getUserByUsername(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserMapper.toResponseDTO(user);
    }

    public  UserResponseDTO findUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserMapper.toResponseDTO(user);
    }

    public UserResponseDTO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserMapper.toResponseDTO(user);
    }

    public List<TeamResponseDTO> getUserTeams(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getUserTeams()
                .stream()
                .map(TeamMapper::toResponseDTO)
                .toList();
    }

    // --- Update ---
    public UserResponseDTO updateUser(Long id, UserUpdateDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
            String newEmail = dto.getEmail().trim();
            if(userRepository.existsByEmailAndIdNot(newEmail, user.getId())) {
                throw new ConflictException("The new emails already exists");
            }
            user.setEmail(newEmail);
        }

        if (dto.getCurrentPassword() != null && dto.getNewPassword() != null) {
            if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new UnauthorizedActionException("Current password does not match");
            }

            if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new UnauthorizedActionException("New password is the same as the old one");
            }

            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        userRepository.save(user);
        return UserMapper.toResponseDTO(user);
    }

    // --- Delete ---
    public void deleteUser(Long id) {
        User loggedUser = getLoggedUser();

        if (!loggedUser.getId().equals(id)) {
            throw new UnauthorizedActionException("You are not allowed to delete another user's account");
        }

        userRepository.delete(loggedUser);
    }
}
