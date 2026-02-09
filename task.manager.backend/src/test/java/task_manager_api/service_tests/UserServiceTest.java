package task_manager_api.service_tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import task_manager_api.DTO.user.UserCreateDTO;
import task_manager_api.DTO.user.UserResponseDTO;
import task_manager_api.DTO.user.UserUpdateDTO;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.User;
import task_manager_api.model.UserTitle;
import task_manager_api.repository.UserRepository;
import task_manager_api.service.user.UserService;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private User existingUser;

    @BeforeEach
    public void setUp() {
        existingUser = new User();
        existingUser.setUsername("testUsername");
        existingUser.setPassword("encodedPassword");
        existingUser.setFirstName("Mark");
        existingUser.setLastName("Raft");
        existingUser.setEmail("markraft@test.com");

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUserSuccessfully() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("testUsername");
        dto.setPassword("password");
        dto.setFirstName("firstName");
        dto.setLastName("lastName");
        dto.setUserTitle(UserTitle.valueOf("Mr"));

        when(passwordEncoder.encode("password"))
                .thenReturn("encodedPassword");

        UserResponseDTO response = userService.createUser(dto);

        assertNotNull(response);
        assertEquals("testUsername", response.getUsername());
        assertEquals("firstName", response.getFirstName());
        assertEquals("lastName", response.getLastName());
        assertEquals(UserTitle.valueOf("Mr"), response.getUserTitle());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUserSuccessfully() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setFirstName("updatedFirstName");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        UserResponseDTO response = userService.updateUser(1L, dto);

        assertNotNull(response);
        assertEquals("updatedFirstName", response.getFirstName());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUseFails_WhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUser(1L, new UserUpdateDTO())
        );
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenCurrentPasswordDoesNotMatch() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setCurrentPassword("wrongPassword");
        dto.setNewPassword("newPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.updateUser(1L, dto)
        );
        assertEquals("Current password does not match", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenNewPasswordSameAsOldPassword() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setCurrentPassword("password");
        dto.setNewPassword("password");

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("password", "encodedPassword"))
                .thenReturn(true);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.updateUser(1L, dto)
        );
        assertEquals("New password is the same as the old one", ex.getMessage());
    }

    @Test
    void deleteUserSuccessfully() {
        userService.deleteUser(1L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    void getUserByIdSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        UserResponseDTO response = userService.getUserById(1L);

        assertNotNull(response);
        assertEquals("testUsername", response.getUsername());
    }

    @Test
    void getUserByIdFails_WhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(1L)
        );
        assertEquals("User not found", ex.getMessage());
    }
}
