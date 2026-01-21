package task_manager_api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.TeamUpdateDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.model.*;
import task_manager_api.service.TeamService;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@PreAuthorize("isAuthenticated()")
public class TeamController {

    private final TeamService teamService;


    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    // --- Create ---
    @PostMapping
    public ResponseEntity<TeamResponseDTO> createTeam(@RequestBody TeamCreateDTO team) {
        TeamResponseDTO teamResponseDTO = teamService.createTeam(team);
        return ResponseEntity.status(HttpStatus.CREATED).body(teamResponseDTO);
    }

    @PostMapping("/{teamId}/users/{userId}")
    public ResponseEntity<TeamResponseDTO> addUserToTeam(@PathVariable Long teamId,
                                                         @PathVariable Long userId,
                                                         @RequestParam(defaultValue = "MEMBER")TeamRole role) {
        TeamResponseDTO team = teamService.addUserToTeam(teamId, userId, role);
        return ResponseEntity.ok(team);
    }

    // --- Read ---
    @GetMapping
    public ResponseEntity<List<TeamResponseDTO>> getUserTeams() {
        List<TeamResponseDTO> teams = teamService.getAllTeamsForUser();
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<UserMemberDTO>> getTeamMembers(@PathVariable Long teamId) {
        List<UserMemberDTO> members = teamService.getTeamMembers(teamId);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/{teamId}/tasks")
    public ResponseEntity<List<TaskSummaryDTO>> getTeamTasks(@PathVariable Long teamId) {
        List<TaskSummaryDTO> tasks = teamService.getTeamTasks(teamId);
        return ResponseEntity.ok(tasks);
    }

    // --- Update ---
    @PutMapping("/{teamId}")
    public ResponseEntity<TeamResponseDTO> updateTeam(@PathVariable Long teamId,
                                                      @Valid @RequestBody TeamUpdateDTO updatedTeam) {
        TeamResponseDTO updated  = teamService.updateTeam(teamId, updatedTeam);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{teamId}/users/{userId}/role")
    public ResponseEntity<TeamResponseDTO> updateUserRole(@PathVariable Long teamId,
                                                          @PathVariable Long userId,
                                                          @RequestParam TeamRole role) {
        TeamResponseDTO dto = teamService.updateUserRole(teamId, userId, role);
        return ResponseEntity.ok(dto);
    }

    // --- Delete ---
    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(@PathVariable Long teamId) {
        teamService.deleteTeam(teamId);
    }

    @DeleteMapping("/{teamId}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerUserFromTeam(@PathVariable Long teamId, @PathVariable Long userId) {
        teamService.removeUserFromTeam(teamId, userId);
    }
}
