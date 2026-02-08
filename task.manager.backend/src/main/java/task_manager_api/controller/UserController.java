package task_manager_api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.user.UserCreateDTO;
import task_manager_api.DTO.user.UserResponseDTO;
import task_manager_api.DTO.user.UserUpdateDTO;
import task_manager_api.mapper.UserMapper;
import task_manager_api.model.User;
import task_manager_api.service.UserService;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;


    public UserController(UserService userService) {
        this.userService = userService;
    }


    // --- Create ---
    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserCreateDTO dto) {
        UserResponseDTO user = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    // --- Read ---
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> getCurrentUser() {
        User loggedUser = userService.getLoggedUser();
        UserResponseDTO user = UserMapper.toResponseDTO(loggedUser);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("isAuthenticated()")
    public UserResponseDTO findUserByUsername(@PathVariable String username) {
        return userService.getUserByUsername(username);
    }

    @GetMapping("/username/{username}/teams")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamResponseDTO>> getUserTeams(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserTeams(username));
    }

    @GetMapping(params = "email")
    @PreAuthorize("isAuthenticated()")
    public UserResponseDTO findUserByEmail(@PathVariable String email) {
        return userService.findUserByEmail(email);
    }

    // --- Update ---
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated() and (principal.id == #id)")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        UserResponseDTO updatedUser = userService.updateUser(id, dto);
        return ResponseEntity.ok(updatedUser);
    }

    // --- Delete ---
    @PreAuthorize("isAuthenticated() and (principal.id == #id)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
