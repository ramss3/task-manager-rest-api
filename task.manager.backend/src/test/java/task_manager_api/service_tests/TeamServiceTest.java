package task_manager_api.service_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import task_manager_api.DTO.task.TaskSummaryDTO;
import task_manager_api.DTO.team.TeamCreateDTO;
import task_manager_api.DTO.team.TeamResponseDTO;
import task_manager_api.DTO.team.TeamUpdateDTO;
import task_manager_api.DTO.team.UserMemberDTO;
import task_manager_api.exceptions.ConflictException;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;
import task_manager_api.service.team.TeamAccessAuthService;
import task_manager_api.service.team.TeamMembershipPolicy;
import task_manager_api.service.team.TeamService;
import task_manager_api.service.user.UserLookupService;
import task_manager_api.service.user.UserService;

@ExtendWith(MockitoExtension.class)
public class TeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TasksRepository tasksRepository;
    @Mock private TeamMembershipRepository teamMembershipRepository;
    @Mock private UserService userService;
    @Mock private UserLookupService userLookupService;
    @Mock private TeamMembershipPolicy membershipPolicy;
    @Mock private TeamAccessAuthService teamAccessAuthService;

    @InjectMocks
    private TeamService teamService;

    private User loggedUser;

    @BeforeEach
    void setUp() {
        // Mock logged-in user
        loggedUser = createUser(99L, "loggedUser");
        lenient().when(userService.getLoggedUser()).thenReturn(loggedUser);
    }

    private User createUser(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Team createTeam(long id, String name) {
        Team t = new Team();
        t.setId(id);
        t.setName(name);
        t.setMemberships(new ArrayList<>());
        return t;
    }

    private TeamMembership membership(Team team, User user, TeamRole teamRole) {
        TeamMembership teamMembership = new TeamMembership();
        teamMembership.setTeam(team);
        teamMembership.setUser(user);
        teamMembership.setTeamRole(teamRole);
        return teamMembership;
    }

    private Team givenTeamExists(long teamId) {
        Team t = createTeam(teamId, "team-" + teamId);
        when(teamAccessAuthService.requireTeam(teamId)).thenReturn(t);
        return t;
    }

    private TeamMembership givenLoggedMembership(Team t, TeamRole role) {
        TeamMembership m = membership(t, loggedUser, role);
        when(teamAccessAuthService.requireMembership(t, loggedUser)).thenReturn(m);
        return m;
    }

    private User givenUserFound(long userId, String username) {
        User u = createUser(userId, username);
        when(userLookupService.requireUser(userId)).thenReturn(u);
        return u;
    }

    // --------------------------------------------------------------------
    //                    createTeam(TeamCreateDTO) tests
    // --------------------------------------------------------------------

    @Test
    void createTeam_ShouldCreateTeamAndOwnerMembership() {
        TeamCreateDTO dto = new TeamCreateDTO();
        dto.setName("testTeam");

        Team saved = createTeam(1L, "testTeam");
        when(teamRepository.save(any(Team.class))).thenReturn(saved);

        // make createMembership return a membership so the service can add it
        TeamMembership owner = membership(saved, loggedUser, TeamRole.OWNER);
        when(teamAccessAuthService.createMembership(saved, loggedUser, TeamRole.OWNER))
                .thenReturn(owner);

        TeamResponseDTO result = teamService.createTeam(dto);

        assertNotNull(result);
        assertEquals("testTeam", result.getTeamName());
        assertEquals(1, saved.getMemberships().size());
        assertSame(owner, saved.getMemberships().get(0));

        verify(teamRepository).save(argThat(t -> "testTeam".equals(t.getName())));
        verify(teamAccessAuthService).createMembership(saved, loggedUser, TeamRole.OWNER);
        verifyNoInteractions(membershipPolicy);
    }

    // -------------------------------------------------------------------
    //                     getAllTeamsForUser() test
    // -------------------------------------------------------------------
    @Test
    void getAllTeamsForUser_ShouldReturnAllTeamsFromMemberships() {
        Team t1 = createTeam(1L, "team1");
        Team t2 = createTeam(2L, "team2");

        when(teamMembershipRepository.findByUser(loggedUser))
                .thenReturn(List.of(
                        membership(t1, loggedUser, TeamRole.OWNER),
                        membership(t2, loggedUser, TeamRole.MEMBER)
                ));

        List<TeamResponseDTO> result = teamService.getAllTeamsForUser();

        assertEquals(2, result.size());
        List<String> names = result.stream().map(TeamResponseDTO::getTeamName).toList();
        assertTrue(names.containsAll(List.of("team1", "team2")));

        verify(teamMembershipRepository).findByUser(loggedUser);
        verifyNoInteractions(teamAccessAuthService, membershipPolicy, teamRepository, tasksRepository, userLookupService);
    }

    // ----------------------------------------------------------------------
    //        updateTeamRole(Long teamId, Long userId, TeamRole role)
    // ----------------------------------------------------------------------
    @Test
    void updateTeam_ShouldSucceed_WhenOwner() {
        Team t = givenTeamExists(1L);
        givenLoggedMembership(t, TeamRole.OWNER);

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        when(teamRepository.save(t)).thenReturn(t);

        TeamResponseDTO result = teamService.updateTeam(1L, dto);

        assertNotNull(result);
        assertEquals("Updated Team Name", result.getTeamName());
        verify(teamRepository).save(t);
    }

    @Test
    void updateTeam_ShouldFail_WhenNotOwner() {
        Team t = givenTeamExists(1L);
        givenLoggedMembership(t, TeamRole.ADMIN);

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateTeam(1L, dto)
        );

        assertEquals("Only the owner can update the team", ex.getMessage());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateTeam_ShouldPropagate_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.updateTeam(1L, dto)
        );

        assertEquals("Team not found", ex.getMessage());
        verifyNoInteractions(teamRepository);
    }

    @Test
    void updateTeam_ShouldFail_WhenLoggedUserNotMember() {
        Team t = givenTeamExists(1L);

        when(teamAccessAuthService.requireMembership(t, loggedUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateTeam(1L, dto)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
        verify(teamRepository, never()).save(any());
    }

    // --------------------------------------------------------------------------
    //                           deleteTeam(Long teamId) tests
    // --------------------------------------------------------------------------
    @Test
    void deleteTeam_ShouldSucceed_WhenOwner() {
        Team t = givenTeamExists(1L);
        givenLoggedMembership(t, TeamRole.OWNER);

        teamService.deleteTeam(1L);

        verify(teamMembershipRepository).deleteAllByTeam(t);
        verify(teamRepository).delete(t);
    }

    @Test
    void deleteTeam_ShouldFail_WhenNotOwner() {
        Team t = givenTeamExists(1L);
        givenLoggedMembership(t, TeamRole.ADMIN);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.deleteTeam(1L)
        );

        assertEquals("Only the team owner can delete the team", ex.getMessage());
        verify(teamMembershipRepository, never()).deleteAllByTeam(any());
        verify(teamRepository, never()).delete(any(Team.class));
    }

    @Test
    void deleteTeam_ShouldPropagate_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.deleteTeam(1L)
        );

        assertEquals("Team not found", ex.getMessage());
        verifyNoInteractions(teamRepository, teamMembershipRepository);
    }

    @Test
    void deleteTeam_ShouldFail_WhenLoggedUserNotMember() {
        Team t = givenTeamExists(1L);

        when(teamAccessAuthService.requireMembership(t, loggedUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.deleteTeam(1L)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
        verify(teamRepository, never()).delete(any(Team.class));
        verify(teamMembershipRepository, never()).deleteAllByTeam(any());
    }

    // ----------------------------------------------------------------------------
    //          addUserToTeam(Long, String, TeamRole)
    // ----------------------------------------------------------------------------
    @Test
    void addUserToTeam_ShouldAddMember_WhenAllowed() {
        Team t = givenTeamExists(1L);
        TeamMembership logged = givenLoggedMembership(t, TeamRole.OWNER);

        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can add new user to the team");

        User newUser = createUser(2L, "newUser");
        when(userLookupService.searchByIdentifier("newUser")).thenReturn(newUser);
        when(teamAccessAuthService.isMember(t, newUser)).thenReturn(false);

        TeamMembership created = membership(t, newUser, TeamRole.MEMBER);
        when(teamAccessAuthService.createMembership(t, newUser, TeamRole.MEMBER)).thenReturn(created);

        TeamResponseDTO result = teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER);

        assertNotNull(result);
        assertEquals(1, t.getMemberships().size());
        assertSame(created, t.getMemberships().get(0));

        verify(membershipPolicy).requireCanManageMembers(logged, "Only the owner or admins can add new user to the team");
        verify(teamRepository).save(t);
    }

    @Test
    void addUserToTeam_ShouldDefaultRoleToMember_WhenRoleIsNull() {
        Team t = givenTeamExists(1L);
        TeamMembership logged = givenLoggedMembership(t, TeamRole.OWNER);

        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can add new user to the team");

        User newUser = createUser(2L, "newUser");
        when(userLookupService.searchByIdentifier("newUser")).thenReturn(newUser);
        when(teamAccessAuthService.isMember(t, newUser)).thenReturn(false);

        TeamMembership created = membership(t, newUser, TeamRole.MEMBER);
        when(teamAccessAuthService.createMembership(t, newUser, TeamRole.MEMBER)).thenReturn(created);

        teamService.addUserToTeam(1L, "newUser", null);

        verify(teamAccessAuthService).createMembership(t, newUser, TeamRole.MEMBER);
    }

    @Test
    void addUserToTeam_ShouldFail_WhenAdminTriesToAssignOwner() {
        Team t = givenTeamExists(1L);
        TeamMembership logged = givenLoggedMembership(t, TeamRole.ADMIN);

        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can add new user to the team");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.OWNER)
        );

        assertEquals("Only the owner can assign a new owner", ex.getMessage());
        verifyNoInteractions(userLookupService);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void addUserToTeam_ShouldFail_WhenUserAlreadyInTeam() {
        Team t = givenTeamExists(1L);
        TeamMembership logged = givenLoggedMembership(t, TeamRole.OWNER);

        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can add new user to the team");

        User newUser = createUser(2L, "newUser");
        when(userLookupService.searchByIdentifier("newUser")).thenReturn(newUser);
        when(teamAccessAuthService.isMember(t, newUser)).thenReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("User is already in the team", ex.getMessage());
        verify(teamRepository, never()).save(any());
        verify(teamAccessAuthService, never()).createMembership(any(), any(), any());
    }

    @Test
    void addUserToTeam_ShouldFail_WhenPolicyDenies() {
        Team t = givenTeamExists(1L);
        TeamMembership logged = givenLoggedMembership(t, TeamRole.MEMBER);

        doThrow(new UnauthorizedActionException("Only the owner or admins can add new user to the team"))
                .when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can add new user to the team");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("Only the owner or admins can add new user to the team", ex.getMessage());
        verifyNoInteractions(userLookupService);
        verify(teamRepository, never()).save(any());
    }

    @Test
    void addUserToTeam_ShouldPropagate_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // updateUserRole(Long, Long, TeamRole)
    // ------------------------------------------------------------------

    @Test
    void updateUserRole_ShouldFail_WhenNewRoleIsNull() {
        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> teamService.updateUserRole(1L, 2L, null)
        );
        assertEquals("New role cannot be null", ex.getMessage());
        verifyNoInteractions(teamAccessAuthService, membershipPolicy, userLookupService, teamMembershipRepository);
    }

    @Test
    void updateUserRole_ShouldSucceed_WhenPolicyAllows() {
        Team t = givenTeamExists(1L);

        TeamMembership logged = givenLoggedMembership(t, TeamRole.OWNER);
        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can update a user role");

        User target = givenUserFound(2L, "target");
        TeamMembership targetMembership = membership(t, target, TeamRole.MEMBER);
        when(teamAccessAuthService.requireMembership(t, target)).thenReturn(targetMembership);

        doNothing().when(membershipPolicy)
                .validateRoleChange(loggedUser, logged, target, targetMembership, TeamRole.ADMIN);

        TeamResponseDTO result = teamService.updateUserRole(1L, 2L, TeamRole.ADMIN);

        assertNotNull(result);
        assertEquals(TeamRole.ADMIN, targetMembership.getTeamRole());
        verify(teamMembershipRepository).save(targetMembership);
    }

    @Test
    void updateUserRole_ShouldFail_WhenValidateRoleChangeDenies() {
        Team t = givenTeamExists(1L);

        TeamMembership logged = givenLoggedMembership(t, TeamRole.ADMIN);
        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can update a user role");

        User target = givenUserFound(2L, "target");
        TeamMembership targetMembership = membership(t, target, TeamRole.MEMBER);
        when(teamAccessAuthService.requireMembership(t, target)).thenReturn(targetMembership);

        doThrow(new UnauthorizedActionException("Only the owner can modify ownership role"))
                .when(membershipPolicy)
                .validateRoleChange(loggedUser, logged, target, targetMembership, TeamRole.OWNER);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.OWNER)
        );

        assertEquals("Only the owner can modify ownership role", ex.getMessage());
        verify(teamMembershipRepository, never()).save(any());
    }

    // ------------------------------------------------------------------------
    //             removeUserFromTeam(Long teamId, Long userId) tests
    // ------------------------------------------------------------------------
    @Test
    void removeUserFromTeam_ShouldSucceed_WhenAllowedAndTargetNotOwner() {
        Team t = givenTeamExists(1L);

        TeamMembership logged = givenLoggedMembership(t, TeamRole.ADMIN);
        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can delete a user");

        User target = givenUserFound(2L, "target");
        TeamMembership targetMembership = membership(t, target, TeamRole.MEMBER);
        when(teamAccessAuthService.requireMembership(t, target)).thenReturn(targetMembership);

        t.getMemberships().add(targetMembership);

        teamService.removeUserFromTeam(1L, 2L);

        verify(teamMembershipRepository).delete(targetMembership);
        verify(teamRepository).save(t);
        assertFalse(t.getMemberships().contains(targetMembership));
    }

    @Test
    void removeUserFromTeam_ShouldFail_WhenTryingToRemoveOwner() {
        Team t = givenTeamExists(1L);

        TeamMembership logged = givenLoggedMembership(t, TeamRole.ADMIN);
        doNothing().when(membershipPolicy)
                .requireCanManageMembers(logged, "Only the owner or admins can delete a user");

        User owner = givenUserFound(2L, "owner");
        TeamMembership ownerMembership = membership(t, owner, TeamRole.OWNER);
        when(teamAccessAuthService.requireMembership(t, owner)).thenReturn(ownerMembership);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.removeUserFromTeam(1L, 2L)
        );

        assertEquals("You cannot remove the team owner", ex.getMessage());
        verify(teamMembershipRepository, never()).delete(any());
        verify(teamRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    //                   getTeamMembers(Long teamId) tests
    // ------------------------------------------------------------------
    @Test
    void getTeamMembers_ShouldReturnMembers_WhenLoggedUserIsMember() {
        Team t = givenTeamExists(1L);
        givenLoggedMembership(t, TeamRole.MEMBER);

        User u1 = createUser(1L, "u1");
        User u2 = createUser(2L, "u2");

        TeamMembership m1 = membership(t, u1, TeamRole.MEMBER);
        TeamMembership m2 = membership(t, u2, TeamRole.OWNER);

        when(teamMembershipRepository.findByTeam(t)).thenReturn(List.of(m1, m2));

        List<UserMemberDTO> result = teamService.getTeamMembers(1L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(m -> m.getRole() == TeamRole.MEMBER));
        assertTrue(result.stream().anyMatch(m -> m.getRole() == TeamRole.OWNER));

        verify(teamAccessAuthService).requireMembership(t, loggedUser);
        verify(teamMembershipRepository).findByTeam(t);
    }

    // ------------------------------------------------------------------
    // getTeamTasks(Long)
    // ------------------------------------------------------------------
    @Test
    void getTeamTasks_ShouldReturnTaskSummaries_WhenMember() {
        Team t = givenTeamExists(1L);
        givenLoggedMembership(t, TeamRole.MEMBER);

        Task task1 = new Task();
        task1.setId(1);
        task1.setTitle("task1");
        task1.setDescription("d1");
        task1.setTeam(t);

        Task task2 = new Task();
        task2.setId(2);
        task2.setTitle("task2");
        task2.setDescription("d2");
        task2.setTeam(t);

        when(tasksRepository.findByTeam(t)).thenReturn(List.of(task1, task2));

        List<TaskSummaryDTO> result = teamService.getTeamTasks(1L);

        assertEquals(2, result.size());
        assertEquals("task1", result.get(0).getTitle());
        assertEquals("task2", result.get(1).getTitle());

        verify(teamAccessAuthService).requireMembership(t, loggedUser);
        verify(tasksRepository).findByTeam(t);
    }
}
