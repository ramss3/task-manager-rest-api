package task_manager_api.controller_tests;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.controller.TeamController;
import task_manager_api.model.TeamRole;
import task_manager_api.security.JwtAuthenticationFilter;
import task_manager_api.security.JwtTokenProvider;
import task_manager_api.service.TeamService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TeamController.class)
@AutoConfigureMockMvc(addFilters = false)// if needed
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    // Mock the security-related beans
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

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
    void testAddUserToTeam() throws Exception {
        TeamResponseDTO team = new TeamResponseDTO();
        team.setTeamName("Team 1");
        Long teamId = 1L;
        Long userId = 2L;
        team.setTeamId(teamId);

        Mockito.when(teamService.addUserToTeam(teamId, userId, TeamRole.ADMIN)).thenReturn(team);

        mockMvc.perform(post("/api/teams/{teamId}/users/{userId}", teamId, userId)
                        .param("role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Team 1"));

        verify(teamService).addUserToTeam(teamId,userId, TeamRole.ADMIN);
    }


}

