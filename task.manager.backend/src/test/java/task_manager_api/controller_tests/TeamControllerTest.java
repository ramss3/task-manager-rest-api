package task_manager_api.controller_tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.controller.TeamController;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.Status;
import task_manager_api.model.TeamRole;
import task_manager_api.security.JwtAuthenticationFilter;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.TeamService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TeamController.class)
@AutoConfigureMockMvc(addFilters = false)// if needed
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TeamService teamService;

    // Mock the security-related beans
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // --- Create ---
    @Test
    void testCreateTeam() throws Exception {
        TeamCreateDTO createTeamDTO = new TeamCreateDTO();
        createTeamDTO.setTeamName("teamName");

        TeamResponseDTO responseDTO = new TeamResponseDTO();
        responseDTO.setTeamName("teamName");

        Mockito.when(teamService.createTeam(any(TeamCreateDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createTeamDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.teamName").value(responseDTO.getTeamName()));
    }

    @Test
    void testAddUserToTeam_WithRole() throws Exception {
        Long teamId = 1L;
        String username = "John";
        Long userId = 2L;
        TeamRole role = TeamRole.ADMIN;

        TeamResponseDTO responseDTO = new TeamResponseDTO();

        responseDTO.setTeamName("teamName");

        Mockito.when(teamService.addUserToTeam(teamId, username, role)).thenReturn(responseDTO);

        mockMvc.perform(post("/api/teams/{teamId}/users/{userId}", teamId, userId)
                        .param("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.teamName").value("teamName"));

        verify(teamService).addUserToTeam(teamId, username, role);

        verify(teamService).addUserToTeam(teamId,username, role);
    }

    @Test
    void testAddUserToTeam_ForbiddenForMembers() throws Exception {
        Long teamId = 1L;
        Long userId = 2L;
        String username = "John";

        Mockito.when(teamService.addUserToTeam(anyLong(), anyString(), any())).thenThrow(new UnauthorizedActionException("Only the owner or admins can add new user"));

        mockMvc.perform(post("/api/teams/{teamId}/users/{userId}", teamId, userId))
                .andExpect(status().isForbidden());

        verify(teamService).addUserToTeam(teamId, username, TeamRole.MEMBER);
    }

    // --- Read ---
    @Test
    void testGetUserTeams() throws Exception {
        TeamResponseDTO t1 = new TeamResponseDTO();
        t1.setTeamName("Team 1");

        Mockito.when(teamService.getAllTeamsForUser()).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Team 1"));
    }

    @Test
    void testRemoveUserFromTeam() throws Exception {

    }

    @Test
    void testGetTeamMembers() throws Exception {
        UserMemberDTO member = new UserMemberDTO(1L, "testuser", "test@example.com", TeamRole.MEMBER);

        Mockito.when(teamService.getTeamMembers(1L)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));
    }

    @Test
    void testGetTeamTasks() throws Exception {
        TaskSummaryDTO task = TaskSummaryDTO.builder()
                .id(101)
                .title("Complete task")
                .status(Status.IN_PROGRESS)
                .build();

        Mockito.when(teamService.getTeamTasks(1L)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/teams/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].title").value("Complete task"));
    }


}

