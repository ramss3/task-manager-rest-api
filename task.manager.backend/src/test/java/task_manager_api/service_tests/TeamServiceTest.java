package task_manager_api.service_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import task_manager_api.repository.UserRepository;
import task_manager_api.service.team.TeamAccessAuthService;
import task_manager_api.service.team.TeamService;
import task_manager_api.service.user.UserLookupService;
import task_manager_api.service.user.UserService;

@SpringBootTest
public class TeamServiceTest {

    @MockitoBean
    private TeamRepository teamRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private TeamMembershipRepository teamMembershipRepository;

    @MockitoBean
    private TasksRepository tasksRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserLookupService userLookupService;

    @MockitoBean
    private TeamAccessAuthService teamAccessAuthService;

    @Autowired
    private TeamService teamService;

    private User loggedUser;

    @BeforeEach
    void setUp() {
        // Mock logged-in user
        loggedUser = createUser(99L, "loggedUser");
        lenient().when(userService.getLoggedUser()).thenReturn(loggedUser);
    }

    @AfterEach
    void tearDown() {
        clearInvocations(
                teamRepository, userRepository, teamMembershipRepository,
                tasksRepository, userService, userLookupService, teamAccessAuthService
        );
    }

    private User createUser(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Team createTeam(long teamId) {
        Team team = new Team();
        team.setId(teamId);
        team.setMemberships(new ArrayList<>());

        lenient().when(teamAccessAuthService.requireTeam(teamId)).thenReturn(team);
        return team;
    }

    private TeamMembership createTeamMembership(Team team, User user, TeamRole teamRole) {
        TeamMembership teamMembership = new TeamMembership();
        teamMembership.setTeam(team);
        teamMembership.setUser(user);
        teamMembership.setTeamRole(teamRole);

        return teamMembership;
    }

    private Team mockTeamAndLoggedUserMembership(long teamId, TeamRole role) {
        Team team = createTeam(teamId);

        if (role != null) {
            TeamMembership membership = createTeamMembership(team, loggedUser, role);
            when(teamAccessAuthService.requireMembership(team, loggedUser)).thenReturn(membership);
        } else {
            when(teamAccessAuthService.requireMembership(team, loggedUser))
                    .thenThrow(new UnauthorizedActionException("You are not a member of this team"));
        }

        return team;
    }

    private User mockUserFound(Long id, String username) {
        User user = createUser(id, username);
        when(userLookupService.requireUser(id)).thenReturn(user);
        return user;
    }

    // --------------------------------------------------------------------
    //                    createTeam(TeamCreateDTO) tests
    // --------------------------------------------------------------------

    @Test
    void createTeamSuccessfully() {
        TeamCreateDTO dto = new TeamCreateDTO();
        dto.setTeamName("testTeam");

        Team savedTeam = new Team();
        savedTeam.setId(1L);
        savedTeam.setName("testTeam");
        savedTeam.setMemberships(new ArrayList<>());

        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);

        // make createMembership return a membership so the service can add it
        TeamMembership ownerMembership = createTeamMembership(savedTeam, loggedUser, TeamRole.OWNER);
        when(teamAccessAuthService.createMembership(savedTeam, loggedUser, TeamRole.OWNER))
                .thenReturn(ownerMembership);

        TeamResponseDTO result = teamService.createTeam(dto);

        assertNotNull(result);
        assertEquals("testTeam", result.getTeamName());

        verify(teamRepository).save(any(Team.class));
        verify(teamAccessAuthService).createMembership(savedTeam, loggedUser, TeamRole.OWNER);
    }

    @Test
    void createTeamHandlesRepositoryReturningNull() {
        TeamCreateDTO dto = new TeamCreateDTO();
        dto.setTeamName("Null team");

        when(teamRepository.save(any(Team.class))).thenReturn(null);

        assertThrows(NullPointerException.class, () -> teamService.createTeam(dto));
    }

    // -------------------------------------------------------------------
    //                     getAllTeamsForUser() test
    // -------------------------------------------------------------------

    @Test
    void getAllTeamsForUserSuccessfully() {
        Team team1 = createTeam(1L);
        team1.setName("team1");
        Team team2 = createTeam(2L);
        team2.setName("team2");
        Team team3 = createTeam(3L);
        team3.setName("team3");

        TeamMembership membership1 = createTeamMembership(team1, loggedUser, TeamRole.OWNER);
        TeamMembership membership2 = createTeamMembership(team2, loggedUser, TeamRole.ADMIN);
        TeamMembership membership3 = createTeamMembership(team3, loggedUser, TeamRole.MEMBER);

        when(teamMembershipRepository.findByUser(loggedUser))
                .thenReturn(List.of(membership1, membership2, membership3));

        List<TeamResponseDTO> result = teamService.getAllTeamsForUser();

        assertNotNull(result);
        assertEquals(3, result.size());

        System.out.println("\n=== Teams for Logged User ===");
        result.forEach(teamDTO ->
                System.out.println("Team: " + teamDTO.getTeamName())
        );

        List<String> teamNames = result.stream().map(TeamResponseDTO::getTeamName).toList();
        assertTrue(teamNames.containsAll(List.of("team1", "team2", "team3")));

        verify(teamMembershipRepository).findByUser(loggedUser);

    }

    // ----------------------------------------------------------------------------
    //          addUserToTeam(Long teamId, Long userId, TeamRole role) tests
    // ----------------------------------------------------------------------------

    @Test
    void addMemberSuccessfully_WhenLoggedUserIsOwner() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        User newUser = createUser(2L, "newUser");

        when(userLookupService.searchByIdentifier("newUser")).thenReturn(newUser);
        when(teamAccessAuthService.isMember(team, newUser)).thenReturn(false);

        TeamMembership created = createTeamMembership(team, newUser, TeamRole.MEMBER);
        when(teamAccessAuthService.createMembership(team, newUser, TeamRole.MEMBER)).thenReturn(created);

        when(teamRepository.save(team)).thenReturn(team);

        TeamResponseDTO result = teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER);

        assertNotNull(result);
        verify(teamAccessAuthService).createMembership(team, newUser, TeamRole.MEMBER);
        verify(teamRepository).save(team);
    }

    @Test
    void addMemberFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenLoggedMemberIsNotMemberOfTeam() {
        mockTeamAndLoggedUserMembership(5L, null);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(5L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenLoggedUserIsMember() {
        mockTeamAndLoggedUserMembership(1L, TeamRole.MEMBER);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("Only the owner or admins can add new user to the team", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenAdminTriesToAdduserAsOwner() {
        mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.OWNER)
        );

        assertEquals("Only the owner can assign a new owner", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenUserNotFound() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        when(userLookupService.searchByIdentifier("newUser"))
                .thenThrow(new ResourceNotFoundException("User not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenUserAlreadyInTeam() {
        Team team = createTeam(1L);
        User target = createUser(2L, "newUser");

        TeamMembership ownerMembership = createTeamMembership(team, loggedUser, TeamRole.OWNER);

        when(userService.getLoggedUser()).thenReturn(loggedUser);
        when(teamAccessAuthService.requireTeam(team.getId())).thenReturn(team);
        when(teamAccessAuthService.requireMembership(team, loggedUser)).thenReturn(ownerMembership);

        when(userLookupService.searchByIdentifier("newUser")).thenReturn(target);

        when(teamAccessAuthService.isMember(team, target)).thenReturn(true);

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> teamService.addUserToTeam(1L, "newUser", TeamRole.MEMBER)
        );

        assertEquals("User is already in the team", ex.getMessage());
    }

    // ----------------------------------------------------------------------
    //        updateTeamRole(Long teamId, Long userId, TeamRole role)
    // ----------------------------------------------------------------------

    @Test
    void updateUserRoleSuccessfully_WhenOwnerUpdatesMemberToAdmin() {
        Team team = createTeam(1L);

        TeamMembership loggedMembership = createTeamMembership(team, loggedUser, TeamRole.OWNER);
        when(teamAccessAuthService.requireMembership(team, loggedUser)).thenReturn(loggedMembership);

        User userToUpdate = mockUserFound(2L, "member");
        TeamMembership memberToUpdate = createTeamMembership(team, userToUpdate, TeamRole.MEMBER);
        when(teamAccessAuthService.requireMembership(team, userToUpdate)).thenReturn(memberToUpdate);

        TeamResponseDTO result = teamService.updateUserRole(team.getId(), userToUpdate.getId(), TeamRole.ADMIN);

        verify(teamMembershipRepository).save(memberToUpdate);
        assertEquals(TeamRole.ADMIN, memberToUpdate.getTeamRole());
        assertNotNull(result);
    }

    @Test
    void updateUserRoleFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.updateUserRole(1L, loggedUser.getId(), TeamRole.ADMIN)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenLoggedUserIsNotInTeam() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, loggedUser.getId(), TeamRole.MEMBER)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenLoggedUserIsMember() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.MEMBER);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(team.getId(), loggedUser.getId(), TeamRole.MEMBER)
        );
        assertEquals("Only the owner or admins can update a user role", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenUserNotFound() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        when(userLookupService.requireUser(2L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.ADMIN)
        );

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenUserToUpdateNotInTeam() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        User target = mockUserFound(2L, "target");

        when(teamAccessAuthService.requireMembership(team, target))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.ADMIN)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenAdminTriesToPromoteToOwner() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.ADMIN));

        User target = mockUserFound(2L, "target");

        TeamMembership targetMembership = createTeamMembership(team, target, TeamRole.ADMIN);
        when(teamAccessAuthService.requireMembership(team, target)).thenReturn(targetMembership);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.OWNER)
        );

        assertEquals("Only the owner can modify ownership role", ex.getMessage());
    }

    @Test
    void updateRoleFails_WhenAdminTriesToDemoteOwner() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);
        User user = mockUserFound(2L, "target");

        when(teamAccessAuthService.requireMembership(team, user))
                .thenReturn(createTeamMembership(team, user, TeamRole.OWNER));

        when(userLookupService.requireUser(user.getId())).thenReturn(user);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.OWNER)
        );

        assertEquals("Only the owner can modify ownership role", ex.getMessage());
    }

    @Test
    void updateRoleFails_WhenOwnerTriesToDemotesSelf() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        when(userLookupService.requireUser(loggedUser.getId())).thenReturn(loggedUser);

        // membershipToUpdate is the same membership as loggedMembership in this case
        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, loggedUser.getId(), TeamRole.MEMBER)
        );

        assertEquals("Owner cannot demote themselves", ex.getMessage());
    }

    // ------------------------------------------------------------------------
    //             removeUserFromTeam(Long teamId, Long userId) tests
    // ------------------------------------------------------------------------

    @Test
    void removeUserSuccessfully_WhenOwnerRemovesMember() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        User target = mockUserFound(1L, "target");

        TeamMembership targetMembership = createTeamMembership(team, target, TeamRole.MEMBER);
        team.getMemberships().add(targetMembership);

        when(teamAccessAuthService.requireMembership(team, target)).thenReturn(targetMembership);

        teamService.removeUserFromTeam(1L, 1L);

        verify(teamMembershipRepository).delete(targetMembership);
        verify(teamRepository).save(team);
    }

    @Test
    void removerUserFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.removeUserFromTeam(1L, 1L)
        );
        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void removerUserFails_WhenLoggedUserIsNotInTeam() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.removeUserFromTeam(1L, 1L)
        );
        assertEquals("You are not a member of this team", ex.getMessage());
    }


    @Test
    void removeUserFails_WhenLoggedUserIsMember() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.MEMBER);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.removeUserFromTeam(team.getId(), loggedUser.getId())
        );
        assertEquals("Only the owner or admins can delete a user", ex.getMessage());
    }

    @Test
    void removeUserFails_WhenUserToBeRemovedNotFound() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        when(userLookupService.requireUser(2L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.removeUserFromTeam(team.getId(), 2L)
        );
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void removeUserFails_WhenUserToBeRemovedIsNotInTeam() {
        Team team = createTeam(1L);
        User targetUser = createUser(2L, "userNotInTeam");

        // logged user is allowed (OWNER or ADMIN)
        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.OWNER));

        // user exists
        when(userLookupService.requireUser(targetUser.getId()))
                .thenReturn(targetUser);

        // but target is NOT in the team
        when(teamAccessAuthService.requireMembership(team, targetUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.removeUserFromTeam(team.getId(), targetUser.getId())
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void removeUserFails_WhenTryingToRemoveOwner() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.ADMIN));

        User ownerUser = mockUserFound(1L, "owner");

        TeamMembership ownerMembership = createTeamMembership(team, ownerUser, TeamRole.OWNER);
        when(teamAccessAuthService.requireMembership(team, ownerUser))
                .thenReturn(ownerMembership);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.removeUserFromTeam(team.getId(), ownerUser.getId())
        );

        assertEquals("You cannot remove the team owner", ex.getMessage());
    }

    // ------------------------------------------------------------------
    //                   getTeamMembers(Long teamId) tests
    // ------------------------------------------------------------------

    @Test
    void getTeamMembersSuccessfully() {
        Long teamId = 1L;
        Team team = createTeam(teamId);

        TeamMembership loggedMembership =
                createTeamMembership(team, loggedUser, TeamRole.ADMIN);

        User user1 = createUser(1L, "user1");
        User user2 = createUser(2L, "user2");

        TeamMembership membership1 = createTeamMembership(team, user1, TeamRole.MEMBER);
        TeamMembership membership2 = createTeamMembership(team, user2, TeamRole.OWNER);

        when(userService.getLoggedUser()).thenReturn(loggedUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.of(loggedMembership));
        when(teamMembershipRepository.findByTeam(team))
                .thenReturn(List.of(membership1, membership2, loggedMembership));

        List<UserMemberDTO> result = teamService.getTeamMembers(teamId);

        assertEquals(3, result.size());
        assertEquals(TeamRole.MEMBER, result.get(0).getRole());
        assertEquals(TeamRole.OWNER, result.get(1).getRole());
        assertEquals(TeamRole.ADMIN, result.get(2).getRole());
    }

    @Test
    void getTeamMembersFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.getTeamMembers(1L)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    // --------------------------------------------------------------------------
    //                      getTeamTasks(Long teamId) tests
    // --------------------------------------------------------------------------

    @Test
    void getTeamTasksSuccessfully() {
        Long teamId = 1L;
        Team team = createTeam(teamId);

        User user = new User();
        user.setId(10L);
        user.setUsername("user");

        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setTeamRole(TeamRole.MEMBER);

        Task task1 = new Task();
        task1.setId(1);
        task1.setTitle("task1");
        task1.setDescription("task1");
        task1.setTeam(team);

        Task task2 = new Task();
        task2.setId(2);
        task2.setTitle("task2");
        task2.setDescription("task2");
        task2.setTeam(team);

        when(userService.getLoggedUser()).thenReturn(user);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByTeamAndUser(team, user)).thenReturn(Optional.of(membership));
        when(tasksRepository.findByTeam(team)).thenReturn(List.of(task1, task2));

        List<TaskSummaryDTO> result = teamService.getTeamTasks(teamId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("task1", result.get(0).getTitle());
        assertEquals("task2", result.get(1).getTitle());
    }

    @Test
    void getTeamTasksFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.getTeamTasks(1L)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void getTeamTasksReturnsEmptyList_WhenNoTasksExist() {
        Long teamId = 1L;
        Team team = createTeam(teamId);

        when(userService.getLoggedUser()).thenReturn(loggedUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.of(createTeamMembership(team, loggedUser, TeamRole.MEMBER)));
        when(tasksRepository.findByTeam(team)).thenReturn(Collections.emptyList());

        List<TaskSummaryDTO> result = teamService.getTeamTasks(teamId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // --------------------------------------------------------------------------
    //            updateTeam(Long teamId, TeamUpdateDTO updatedTeam) tests
    // --------------------------------------------------------------------------

    @Test
    void updateTeamSuccessfully() {
        Team team = createTeam(1L);

        TeamMembership membership = createTeamMembership(team, loggedUser, TeamRole.OWNER);
        when(teamAccessAuthService.requireMembership(team, loggedUser)).thenReturn(membership);

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        when(teamRepository.save(team)).thenReturn(team);

        TeamResponseDTO result = teamService.updateTeam(1L, dto);

        assertNotNull(result);
        assertEquals("Updated Team Name", result.getTeamName());
        verify(teamRepository).save(team);
    }

    @Test
    void updateTeamFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.updateTeam(1L, dto)
        );
        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void updateTeamFails_WhenUserNotMember() {
        long teamId = 1L;
        createTeam(teamId);

        when(teamAccessAuthService.requireMembership(any(Team.class), eq(loggedUser)))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateTeam(teamId, dto)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void updateTeamFails_WhenLoggedUserNotOwner() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenReturn(createTeamMembership(team, loggedUser, TeamRole.ADMIN));

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateTeam(1L, dto)
        );

        assertEquals("Only the owner can update the team", ex.getMessage());
    }

    // --------------------------------------------------------------------------
    //                           deleteTeam(Long teamId) tests
    // --------------------------------------------------------------------------

    @Test
    void deleteTeamSuccessfully() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);

        teamService.deleteTeam(1L);

        verify(teamMembershipRepository).deleteAllByTeam(team);
        verify(teamRepository).delete(team);
    }

    @Test
    void deleteTeamFails_WhenTeamNotFound() {
        when(teamAccessAuthService.requireTeam(1L))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.deleteTeam(1L)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void deleteTeamFails_WhenUserNotMemberOfTeam() {
        Team team = createTeam(1L);

        when(teamAccessAuthService.requireMembership(team, loggedUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.deleteTeam(1L)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void  deleteTeamFails_WhenLoggedUserNotOwnerOfTeam() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.deleteTeam(team.getId())
        );
        assertEquals("Only the team owner can delete the team", ex.getMessage());
    }
}
