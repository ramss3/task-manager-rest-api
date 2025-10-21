package task_manager_api.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import task_manager_api.DTO.user.UserCreateDTO;
import task_manager_api.DTO.user.UserResponseDTO;
import task_manager_api.DTO.user.UserUpdateDTO;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.mapper.UserMapper;
import task_manager_api.model.User;
import task_manager_api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

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

    public UserResponseDTO updateUser(Long id, UserUpdateDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if(dto.getUserTitle() != null) user.setTitle(dto.getUserTitle());
        if(dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if(dto.getLastName() != null) user.setLastName(dto.getLastName());
        if(dto.getUsername() != null) user.setUsername(dto.getUsername());
        if(dto.getEmail() != null) user.setEmail(dto.getEmail());

        if(dto.getCurrentPassword() != null && dto.getNewPassword() != null) {
            if(!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new UnauthorizedActionException("Current password does not match");
            }

            if(passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
                throw new UnauthorizedActionException("New password is the same as the old one");
            }

            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        userRepository.save(user);
        return UserMapper.toResponseDTO(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public User getLoggedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedActionException("User is not authenticated");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserMapper.toResponseDTO(user);
    }
}
