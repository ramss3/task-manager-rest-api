package task_manager_api.service_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
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
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;
import task_manager_api.repository.UserRepository;
import task_manager_api.service.TeamService;
import task_manager_api.service.UserService;

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
        clearInvocations(teamRepository, userRepository, teamMembershipRepository, tasksRepository, userService);
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

        lenient().when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
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

        if(role != null) {
            TeamMembership membership = createTeamMembership(team, loggedUser, role);
            when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                    .thenReturn(Optional.of(membership));
        } else {
            when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                    .thenReturn(Optional.empty());
        }

        return team;
    }

    private User mockUserFound(Long id, String username) {
        User user = createUser(id, username);
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user));
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

        TeamResponseDTO result = teamService.createTeam(dto);

        // Verify if team was correctly saved
        assertNotNull(result);
        assertEquals("testTeam", result.getTeamName());

        // Verify repository calls
        verify(teamRepository).save(any(Team.class));

        // Capture the membership saved
        ArgumentCaptor<TeamMembership> membershipCaptor = ArgumentCaptor.forClass(TeamMembership.class);
        verify(teamMembershipRepository).save(membershipCaptor.capture());

        // Get the team membership value
        TeamMembership capturedMembership = membershipCaptor.getValue();

        // Verify if membership is OWNER
        assertNotNull(capturedMembership);
        assertEquals(TeamRole.OWNER, capturedMembership.getTeamRole());
        assertEquals(loggedUser, capturedMembership.getUser());
        assertEquals(savedTeam, capturedMembership.getTeam());
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
        User newUser = mockUserFound(2L, "newUser");

        when(teamMembershipRepository.existsByTeamAndUser(team, newUser))
                .thenReturn(false);

        when(teamRepository.save(team)).thenReturn(team);

        TeamResponseDTO result = teamService.addUserToTeam(1L, 2L, TeamRole.MEMBER);

        assertNotNull(result);
        verify(teamMembershipRepository).save(any(TeamMembership.class));
        verify(teamRepository).save(team);
    }

    @Test
    void addMemberFails_WhenTeamNotFound() {
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.addUserToTeam(1L, 2L, TeamRole.MEMBER)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenLoggedMemberIsNotMemberOfTeam() {

        mockTeamAndLoggedUserMembership(5L, null);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(5L, 2L, TeamRole.MEMBER)
        );

        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenLoggedUserIsMember() {
        mockTeamAndLoggedUserMembership(1L, TeamRole.MEMBER);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(1L, 2L, TeamRole.MEMBER)
        );

        assertEquals("Only the owner or admins can add new user to the team", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenAdminTriesToAdduserAsOwner() {
        mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.addUserToTeam(1L, 2L, TeamRole.OWNER)
        );

        assertEquals("Only the owner can assign a new owner", ex.getMessage());
    }

    @Test
    void addMemberFails_WhenUserNotFound() {
        mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);

        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.addUserToTeam(1L, 2L, TeamRole.MEMBER)
        );

        assertEquals("User not found", ex.getMessage());

    }

    @Test
    void addMemberFails_WhenUserAlreadyInTeam() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        User user = mockUserFound(2L, "newUser");
        when(teamMembershipRepository.existsByTeamAndUser(team, user)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> teamService.addUserToTeam(1L, 2L, TeamRole.MEMBER)
        );

        assertEquals("User is already in the team", ex.getMessage());
    }

    // ----------------------------------------------------------------------
    //        updateTeamRole(Long teamId, Long userId, TeamRole role)
    // ----------------------------------------------------------------------

    @Test
    void updateUserRoleSuccessfully_WhenOwnerUpdatesMemberToAdmin() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        User user = mockUserFound(2L, "member");
        TeamMembership memberToUpdate = createTeamMembership(team, user, TeamRole.MEMBER);

        when(teamMembershipRepository.findByTeamAndUser(team, user))
                .thenReturn(Optional.of(memberToUpdate));

        TeamResponseDTO result = teamService.updateUserRole(team.getId(), user.getId(), TeamRole.ADMIN);

        verify(teamMembershipRepository).save(memberToUpdate);
        assertEquals(TeamRole.ADMIN, memberToUpdate.getTeamRole());
        assertNotNull(result);

    }

    @Test
    void updateUserRoleFails_WhenTeamNotFound() {
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.updateUserRole(1L, loggedUser.getId(), TeamRole.ADMIN)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenLoggedUserIsNotInTeam() {
        Team team = createTeam(1L);
        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.empty());

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
        mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.ADMIN)
        );

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenUserToUpdateNotInTeam() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        User user = mockUserFound(2L, "target");
        when(teamMembershipRepository.findByTeamAndUser(team, user))
                .thenReturn(Optional.empty());

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.ADMIN)
        );
        assertEquals("The desired user is not a member of this team", ex.getMessage());
    }

    @Test
    void updateUserRoleFails_WhenAdminTriesToPromoteToOwner() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);
        User user = mockUserFound(2L, "target");
        TeamMembership memberToUpdate = createTeamMembership(team, user, TeamRole.ADMIN);

        when(teamMembershipRepository.findByTeamAndUser(team, user))
                .thenReturn(Optional.of(memberToUpdate));

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
        TeamMembership memberToUpdate = createTeamMembership(team, user, TeamRole.OWNER);

        when(teamMembershipRepository.findByTeamAndUser(team, user))
                .thenReturn(Optional.of(memberToUpdate));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateUserRole(1L, 2L, TeamRole.OWNER)
        );

        assertEquals("Only the owner can modify ownership role", ex.getMessage());
    }

    @Test
    void updateRoleFails_WhenOwnerTriesToDemotesSelf() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);

        when(userRepository.findById(loggedUser.getId()))
                .thenReturn(Optional.of(loggedUser));

        TeamMembership membership = createTeamMembership(team, loggedUser, TeamRole.OWNER);
        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.of(membership));

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
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        User userToBeRemoved = mockUserFound(1L, "target");

        when(userRepository.findById(1L))
            .thenReturn(Optional.of(userToBeRemoved));

        TeamMembership membership = createTeamMembership(team, userToBeRemoved, TeamRole.MEMBER);
        team.getMemberships().add(membership);

        when(teamMembershipRepository.findByTeamAndUser(team, userToBeRemoved))
            .thenReturn(Optional.of(membership));

        TeamResponseDTO result = teamService.removeUserFromTeam(1L, 1L);

        assertNotNull(result);
        verify(teamMembershipRepository).delete(membership);
        verify(teamRepository).save(team);
    }

    @Test
    void removerUserFails_WhenTeamNotFound() {
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.removeUserFromTeam(1L, 1L)
        );
        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void removerUserFails_WhenLoggedUserIsNotInTeam() {
        Team team = createTeam(1L);

        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
            .thenReturn(Optional.empty());

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
                () -> teamService.removeUserFromTeam(1L, 1L)
        );
        assertEquals("Only the owner or admins can delete a user", ex.getMessage());
    }

    @Test
    void removeUserFails_WhenUserToBeRemovedNotFound() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.removeUserFromTeam(1L, 2L)
        );
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void removeUserFails_WhenUserToBeRemovedIsNotInTeam() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.OWNER);
        User user = createUser(1L, "target");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(teamMembershipRepository.findByTeamAndUser(team, user))
                .thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.removeUserFromTeam(1L, 1L)
        );
        assertEquals("User is not a member of this team", ex.getMessage());
    }

    @Test
    void removeUserFails_WhenTryingToRemoveOwner() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);
        User user = createUser(1L, "owner");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TeamMembership ownerRemoval = createTeamMembership(team, user, TeamRole.OWNER);
        when(teamMembershipRepository.findByTeamAndUser(team, user))
                .thenReturn(Optional.of(ownerRemoval));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.removeUserFromTeam(1L, 1L)
        );
        assertEquals("You cannot remove the team owner", ex.getMessage());
    }

    // ------------------------------------------------------------------
    //                   getTeamMembers(Long teamId) tests
    // ------------------------------------------------------------------

    @Test
    void getTeamMembersSuccessfully() {
        Team team = createTeam(1L);
        User user1 = createUser(1L, "user1");
        User user2 = createUser(2L, "user2");

        TeamMembership membership1 = createTeamMembership(team, user1, TeamRole.MEMBER);
        TeamMembership membership2 = createTeamMembership(team, user2, TeamRole.OWNER);
        TeamMembership membership3 = createTeamMembership(team, loggedUser, TeamRole.ADMIN);

        when(teamMembershipRepository.findByTeam(team))
                .thenReturn(List.of(membership1, membership2, membership3));

        List<UserMemberDTO> result = teamService.getTeamMembers(1L);
        assertNotNull(result);
        assertEquals(3, result.size());

        UserMemberDTO user1Dto = result.get(0);
        UserMemberDTO user2Dto = result.get(1);
        UserMemberDTO user3Dto = result.get(2);

        assertEquals(TeamRole.MEMBER, user1Dto.getRole());
        assertEquals(TeamRole.OWNER, user2Dto.getRole());
        assertEquals(TeamRole.ADMIN, user3Dto.getRole());

        System.out.println("\n=== Members in Team ===");
        result.forEach(members ->
                System.out.println("Member: " + members.getUsername()));
    }

    @Test
    void getTeamMembersFails_WhenTeamNotFound() {
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

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
        Team team = createTeam(1L);

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

        when(tasksRepository.findByTeam(team)).thenReturn(List.of(task1, task2));

        List<TaskSummaryDTO> result = teamService.getTeamTasks(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("task1", result.get(0).getTitle());
        assertEquals("task2", result.get(1).getTitle());

        System.out.println("\n=== Team Tasks ===");
        result.forEach(task -> System.out.println("Task: " + task.getTitle()));
    }

    @Test
    void getTeamTasksFails_WhenTeamNotFound() {
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.getTeamTasks(1L)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void getTeamTasksReturnsEmptyList_WhenNoTasksExist() {
        Team team = createTeam(1L);

        when(tasksRepository.findByTeam(team)).thenReturn(Collections.emptyList());

        List<TaskSummaryDTO> result = teamService.getTeamTasks(1L);

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
        team.getMemberships().add(membership);

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.of(membership));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        TeamResponseDTO result = teamService.updateTeam(1L, dto);

        assertNotNull(result);
        assertEquals("Updated Team Name", result.getTeamName());
        verify(teamRepository).save(team);
    }

    @Test
    void updateTeamFails_WhenTeamNotFound() {
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

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
        Team team = createTeam(1L);
        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.empty());

        TeamUpdateDTO dto = new TeamUpdateDTO();
        dto.setTeamName("Updated Team Name");

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.updateTeam(1L, dto)
        );
        assertEquals("You are not a member of the team", ex.getMessage());
    }

    @Test
    void updateTeamFails_WhenLoggedUserNotOwner() {
        Team team = createTeam(1L);
        TeamMembership membership = createTeamMembership(team, loggedUser, TeamRole.ADMIN);
        team.getMemberships().add(membership);

        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.of(membership));

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
        when(teamRepository.findById(1L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> teamService.deleteTeam(1L)
        );

        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void deleteTeamFails_WhenUserNotMemberOfTeam() {
        Team team = createTeam(1L);
        when(teamMembershipRepository.findByTeamAndUser(team, loggedUser))
                .thenReturn(Optional.empty());

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.deleteTeam(1L)
        );
        assertEquals("You are not a member of the team", ex.getMessage());
    }

    @Test
    void  deleteTeamFails_WhenLoggedUserNotOwnerOfTeam() {
        Team team = mockTeamAndLoggedUserMembership(1L, TeamRole.ADMIN);

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> teamService.deleteTeam(1L)
        );
        assertEquals("Only the team owner can delete the team", ex.getMessage());
    }

}
