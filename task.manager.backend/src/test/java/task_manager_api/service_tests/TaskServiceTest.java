package task_manager_api.service_tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import task_manager_api.DTO.task.TaskCreateDTO;
import task_manager_api.exceptions.ResourceNotFoundException;
import task_manager_api.exceptions.UnauthorizedActionException;
import task_manager_api.model.*;
import task_manager_api.repository.TasksRepository;
import task_manager_api.repository.TeamMembershipRepository;
import task_manager_api.repository.TeamRepository;
import task_manager_api.service.task.TaskService;
import task_manager_api.service.team.TeamAccessAuthService;
import task_manager_api.service.user.UserService;
import task_manager_api.DTO.task.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

@SpringBootTest
public class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @MockitoBean
    private TasksRepository tasksRepository;

    @MockitoBean
    private TeamRepository teamRepository;

    @MockitoBean
    private TeamMembershipRepository membershipRepository;

    @MockitoBean
    private TeamAccessAuthService teamAccessAuthService;

    @MockitoBean
    private UserService userService;

    private Task task;

    private User user;

    private Team team;
    @Autowired
    private TeamMembershipRepository teamMembershipRepository;

    @BeforeEach
    public void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("username");

        team = new Team();
        team.setId(2L);
        team.setName("testTeam");

        task = new Task();
        task.setId(1);
        task.setTitle("Test Task Service");
        task.setDescription("Test Task Service");
        task.setStatus(Status.IN_PROGRESS);
        task.setDateCreated(LocalDateTime.now());
        task.setDeadline(LocalDateTime.now().plusDays(1));
        task.setUser(user);
        task.setTeam(null);
    }

    @Test
    void createPersonalTaskSuccessfully() {
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setTitle("Test Task Service");

        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponseDTO response = taskService.createTask(dto);

        assertNotNull(response);
        assertEquals("Test Task Service", response.getTitle());
        verify(tasksRepository).save(any(Task.class));
    }

    @Test
    void createTaskForTeamSuccessfully() {
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setTitle("Test Task Service");
        dto.setTeamId(team.getId());

        TeamMembership membership = new TeamMembership();
        membership.setTeam(team);
        membership.setUser(user);
        membership.setTeamRole(TeamRole.MEMBER);

        when(userService.getLoggedUser()).thenReturn(user);
        when(teamAccessAuthService.requireTeam(team.getId())).thenReturn(team);
        when(teamAccessAuthService.requireMembership(team, user)).thenReturn(membership);
        when(tasksRepository.save(any(Task.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskResponseDTO response = taskService.createTask(dto);

        assertNotNull(response);
        assertEquals("Test Task Service", response.getTitle());
        verify(tasksRepository).save(any(Task.class));
    }

    @Test
    void createTaskFails_WhenTeamNotFound() {
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setTeamId(team.getId());

        when(userService.getLoggedUser()).thenReturn(user);
        when(teamAccessAuthService.requireTeam(team.getId()))
                .thenThrow(new ResourceNotFoundException("Team not found"));

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.createTask(dto)
        );
        assertEquals("Team not found", ex.getMessage());
    }

    @Test
    void createTaskFails_WhenUserNotTeamMember() {
        TaskCreateDTO dto = new TaskCreateDTO();
        User wrongUser = new User();
        wrongUser.setUsername("wrongUser");
        dto.setTeamId(team.getId());

        when(userService.getLoggedUser()).thenReturn(wrongUser);
        when(teamAccessAuthService.requireTeam(team.getId())).thenReturn(team);
        when(teamAccessAuthService.requireMembership(team, wrongUser))
                .thenThrow(new UnauthorizedActionException("You are not a member of this team"));

        UnauthorizedActionException ex = assertThrows(
                UnauthorizedActionException.class,
                () -> taskService.createTask(dto)
        );
        assertEquals("You are not a member of this team", ex.getMessage());
    }

    @Test
    void getUserTasksSuccessfully() {
        Task task1 = new Task();
        task1.setId(1);
        task1.setTitle("Test Task Title1");
        task1.setUser(user);
        Task task2 = new Task();
        task2.setId(2);
        task2.setTitle("Test Task Title2");

        List<Task> taskList = List.of(task, task1, task2);

        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findByUser(user)).thenReturn(taskList);

        List<TaskSummaryDTO> result = taskService.getUserTasks();

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Test Task Title1", result.get(1).getTitle());

        verify(userService).getLoggedUser();
        verify(tasksRepository).findByUser(user);

    }

    @Test
    void getTasksByIdSuccessfully() {
        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findById(task.getId())).thenReturn(Optional.of(task));

        TaskResponseDTO result = taskService.getTaskById(task.getId());

        assertNotNull(result);
        assertEquals("Test Task Service", result.getTitle());
    }

    @Test
    void getTaskByIdFails_WhenTaskNotFound() {
        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findById(task.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.getTaskById(task.getId())
        );
        assertEquals("Task not found", ex.getMessage());
    }

    @Test
    void updateTaskSuccessfully() {
        TaskUpdateDTO dto = new TaskUpdateDTO();
        dto.setTitle("Updated Title");

        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(tasksRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponseDTO response = taskService.updateTask(task.getId(), dto);

        assertNotNull(response);
        assertEquals("Updated Title", response.getTitle());
        verify(tasksRepository).save(any(Task.class));
    }

    @Test
    void updateTaskFails_WhenTaskNotFound() {
        TaskUpdateDTO dto = new TaskUpdateDTO();

        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findById(task.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.updateTask(task.getId(), dto)
        );
        assertEquals("Task not found", ex.getMessage());
    }

    @Test
    void findByTitleSuccessfully() {
        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findByUserAndTitleContainingIgnoreCase(userService.getLoggedUser(), task.getTitle())).thenReturn(List.of(task));

        List<TaskSummaryDTO> result = taskService.findByTitle(task.getTitle());

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Task Service",  result.get(0).getTitle());
    }

    @Test
    void findByStatusSuccessfully() {
        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findByUserAndStatus(userService.getLoggedUser(), task.getStatus())).thenReturn(List.of(task));

        List<TaskSummaryDTO> result = taskService.findByStatus(task.getStatus());
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Status.IN_PROGRESS,  result.get(0).getStatus());
    }

    @Test
    void deleteTaskSuccessfully() {
        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findById(task.getId())).thenReturn(Optional.of(task));

        taskService.deleteTask(task.getId());
        verify(tasksRepository).delete(task);
    }

    @Test
    void deleteTaskFails_WhenTaskNotFound() {
        when(userService.getLoggedUser()).thenReturn(user);
        when(tasksRepository.findById(task.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> taskService.deleteTask(task.getId())
        );
        assertEquals("Task not found", ex.getMessage());
    }
}
