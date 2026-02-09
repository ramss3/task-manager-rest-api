package task_manager_api.controller_tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.AddTeamMemberDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.controller.TeamController;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.Status;
import task_manager_api.model.TeamRole;
import task_manager_api.security.JwtAuthenticationFilter;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.team.TeamService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    void createTeam_Returns201() throws Exception {
        TeamCreateDTO createTeamDTO = new TeamCreateDTO();
        createTeamDTO.setTeamName("teamName");

        TeamResponseDTO responseDTO = new TeamResponseDTO();
        responseDTO.setTeamId(1L);
        responseDTO.setTeamName("teamName");

        when(teamService.createTeam(any(TeamCreateDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createTeamDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.teamName").value("teamName"));

        verify(teamService).createTeam(any(TeamCreateDTO.class));
    }

    @Test
    void addUserToTeam_WithRole_Returns200() throws Exception {
        Long teamId = 1L;

        AddTeamMemberDTO body = new AddTeamMemberDTO();
        body.setIdentifier("John");

        TeamResponseDTO responseDTO = new TeamResponseDTO();
        responseDTO.setTeamId(teamId);
        responseDTO.setTeamName("teamName");

        when(teamService.addUserToTeam(eq(teamId), eq("John"), eq(TeamRole.ADMIN)))
                .thenReturn(responseDTO);

        mockMvc.perform(post("/api/teams/{teamId}/members/add", teamId)
                        .param("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1L))
                .andExpect(jsonPath("$.teamName").value("teamName"));

        verify(teamService).addUserToTeam(teamId, "John", TeamRole.ADMIN);
    }

    @Test
    void addUserToTeam_Forbidden_Returns403() throws Exception {
        Long teamId = 1L;

        AddTeamMemberDTO body = new AddTeamMemberDTO();
        body.setIdentifier("John");

        when(teamService.addUserToTeam(eq(teamId), eq("John"), any(TeamRole.class)))
                .thenThrow(new UnauthorizedActionException("Only the owner or admins can add new user to the team"));

        mockMvc.perform(post("/api/teams/{teamId}/members/add", teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(teamService).addUserToTeam(teamId, "John", TeamRole.MEMBER); // default role
    }

    // --- Read ---
    @Test
    void getUserTeams_Returns200() throws Exception {
        TeamResponseDTO t1 = new TeamResponseDTO();
        t1.setTeamId(1L);
        t1.setTeamName("Team 1");

        when(teamService.getAllTeamsForUser()).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(1))
                .andExpect(jsonPath("$[0].teamName").value("Team 1"));

        verify(teamService).getAllTeamsForUser();
    }

    @Test
    void getTeamMembers_Returns200() throws Exception {
        UserMemberDTO member = new UserMemberDTO(1L, "testuser", "test@example.com", TeamRole.MEMBER);

        when(teamService.getTeamMembers(1L)).thenReturn(List.of(member));

        mockMvc.perform(get("/api/teams/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));

        verify(teamService).getTeamMembers(1L);
    }

    @Test
    void getTeamTasks_Returns200() throws Exception {
        TaskSummaryDTO task = TaskSummaryDTO.builder()
                .id(101)
                .title("Complete task")
                .status(Status.IN_PROGRESS)
                .build();

        when(teamService.getTeamTasks(1L)).thenReturn(List.of(task));

        mockMvc.perform(get("/api/teams/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].title").value("Complete task"))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));

        verify(teamService).getTeamTasks(1L);
    }

    // --- Delete ---
    @Test
    void deleteTeam_Returns204() throws Exception {
        mockMvc.perform(delete("/api/teams/1"))
                .andExpect(status().isNoContent());

        verify(teamService).deleteTeam(1L);
    }

    @Test
    void removeUserFromTeam_Returns204() throws Exception {
        mockMvc.perform(delete("/api/teams/1/users/2"))
                .andExpect(status().isNoContent());

        verify(teamService).removeUserFromTeam(1L, 2L);
    }

    @Test
    void addUserToTeam_BadRequest_WhenIdentifierBlank() throws Exception {
        AddTeamMemberDTO body = new AddTeamMemberDTO();
        body.setIdentifier("   ");

        mockMvc.perform(post("/api/teams/1/members/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}

