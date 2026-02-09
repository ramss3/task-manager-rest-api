package task_manager_api.service_tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import task_manager_api.DTO.user.UserCreateDTO;
import task_manager_api.DTO.user.UserResponseDTO;
import task_manager_api.DTO.user.UserUpdateDTO;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.User;
import task_manager_api.model.UserTitle;
import task_manager_api.repository.UserRepository;
import task_manager_api.security.UserPrincipal;
import task_manager_api.service.user.UserLookupService;
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

    @MockitoBean
    private UserLookupService  userLookupService;

    private User existingUser;

    @BeforeEach
    public void setUp() {
        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setVerified(true);

        existingUser.setUsername("testUsername");
        existingUser.setPassword("encodedPassword");
        existingUser.setFirstName("Mark");
        existingUser.setLastName("Raft");
        existingUser.setEmail("markraft@test.com");

        authenticateAs(existingUser);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userLookupService.requireUser(1L)).thenReturn(existingUser); // NEW: updateUser/getUserById use this
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- Create tests ---
    @Test
    void createUserSuccessfully() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("testUsername");
        dto.setPassword("password");
        dto.setFirstName("firstName");
        dto.setLastName("lastName");
        dto.setEmail("Test@Email.com");
        dto.setUserTitle(UserTitle.Mr);

        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");

        User saved = new User();
        saved.setId(10L);
        saved.setVerified(true);
        saved.setTitle(UserTitle.Mr);
        saved.setUsername("testUsername");
        saved.setPassword("encodedPassword");
        saved.setFirstName("firstName");
        saved.setLastName("lastName");
        saved.setEmail("test@email.com");

        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponseDTO response = userService.createUser(dto);

        assertNotNull(response);
        assertEquals("testUsername", response.getUsername());
        assertEquals("firstName", response.getFirstName());
        assertEquals("lastName", response.getLastName());
        assertEquals(UserTitle.Mr, response.getUserTitle());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUserFails_WhenDuplicateUsernameOrEmail() {
        UserCreateDTO dto = new UserCreateDTO();
        dto.setUsername("testUsername");
        dto.setPassword("password");
        dto.setFirstName("firstName");
        dto.setLastName("lastName");
        dto.setEmail("test@email.com");
        dto.setUserTitle(UserTitle.Mr);

        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.createUser(dto)
        );

        assertEquals("Username or email already exists", ex.getMessage());
    }

    // --- Read Tests ---
    @Test
    void getUserByIdSuccessfully() {
        when(userLookupService.requireUser(1L)).thenReturn(existingUser);
        UserResponseDTO response = userService.getUserById(1L);

        assertNotNull(response);
        assertEquals("testUsername", response.getUsername());
    }

    @Test
    void getLoggedUserFails_WhenNotAuthenticated() {
        SecurityContextHolder.clearContext();

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.getLoggedUser()
        );

        assertEquals("User is not authenticated", ex.getMessage());
    }

    @Test
    void getUserByIdFails_WhenUserNotFound() {
        when(userLookupService.requireUser(1L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(1L)
        );
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void getLoggedUserFails_WhenPrincipalIsNotUserPrincipal() {
        var auth = new UsernamePasswordAuthenticationToken("anonymousUser", null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.getLoggedUser()
        );

        assertEquals("User is not authenticated", ex.getMessage());
    }

    @Test
    void getLoggedUserFails_WhenLoggedUserNotFoundInDatabase() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getLoggedUser()
        );

        assertEquals("Logged user not found", ex.getMessage());
    }


    // --- Update tests ---
    @Test
    void updateUserSuccessfully() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setFirstName("updatedFirstName");

        when(userLookupService.requireUser(1L)).thenReturn(existingUser);
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        UserResponseDTO response = userService.updateUser(1L, dto);

        assertNotNull(response);
        assertEquals("updatedFirstName", response.getFirstName());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUserSuccessfully_WhenChangingPassword() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setCurrentPassword("oldPass");
        dto.setNewPassword("newPass");

        when(userLookupService.requireUser(1L)).thenReturn(existingUser);
        when(passwordEncoder.matches("oldPass", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.matches("newPass", "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        UserResponseDTO response = userService.updateUser(1L, dto);

        assertNotNull(response);
        assertEquals("encodedNewPass", existingUser.getPassword());
        verify(passwordEncoder).encode("newPass");
        verify(userRepository).save(existingUser);
    }


    @Test
    void updateUseFails_WhenUserNotFound() {
        when(userLookupService.requireUser(1L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUser(1L, new UserUpdateDTO())
        );
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenTryingToUpdateAnotherUser() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setFirstName("hackerChange");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.updateUser(2L, dto)
        );

        assertEquals("You cannot update another user", ex.getMessage());
        verify(userLookupService, never()).requireUser(2L);
    }

    @Test
    void updateUserFails_WhenCurrentPasswordDoesNotMatch() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setCurrentPassword("wrongPassword");
        dto.setNewPassword("newPassword");

        when(userLookupService.requireUser(1L)).thenReturn(existingUser);
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

        when(userLookupService.requireUser(1L)).thenReturn(existingUser);
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true, true);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.updateUser(1L, dto)
        );
        assertEquals("New password is the same as the old one", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenOnlyCurrentPasswordProvided() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setCurrentPassword("currOnly");

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.updateUser(1L, dto)
        );

        assertEquals("To change the password, provide current and new password", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenOnlyNewPasswordProvided() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setNewPassword("newOnly");

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.updateUser(1L, dto)
        );

        assertEquals("To change the password, provide current and new password", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenUsernameAlreadyTaken() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setUsername("takenUsername");

        when(userRepository.existsByUsernameAndIdNot("takenUsername", 1L)).thenReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.updateUser(1L, dto)
        );

        assertEquals("Username is already taken", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenEmailAlreadyExists() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setEmail("taken@email.com");

        when(userRepository.existsByEmailAndIdNot("taken@email.com", 1L)).thenReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.updateUser(1L, dto)
        );

        assertEquals("The new emails already exists", ex.getMessage());
    }

    @Test
    void updateUserFails_WhenDuplicateUsernameOrEmailOnSave() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setFirstName("NewName");

        when(userRepository.save(existingUser)).thenThrow(new DataIntegrityViolationException("dup"));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userService.updateUser(1L, dto)
        );

        assertEquals("Username or email already exists", ex.getMessage());
    }

    // --- Delete tests ---
    @Test
    void deleteUserSuccessfully() {
        userService.deleteUser(1L);
        verify(userRepository).delete(existingUser);
    }

    @Test
    void deleteUserFails_WhenTryingToDeleteAnotherUser() {
        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> userService.deleteUser(2L)
        );

        assertEquals("You are not allowed to delete another user's account", ex.getMessage());
        verify(userRepository, never()).delete(any(User.class));
    }
}
