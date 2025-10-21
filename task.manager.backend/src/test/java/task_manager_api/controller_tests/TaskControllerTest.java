package task_manager_api.controller_tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import task_manager_api.DTO.task.*;
import task_manager_api.controller.TaskController;
import task_manager_api.model.Status;
import task_manager_api.security.JwtAuthenticationFilter;
import task_manager_api.service.TaskService;


import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

import task_manager_api.DTO.task.TaskUpdateDTO;
import task_manager_api.DTO.task.TaskResponseDTO;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


@WebMvcTest(controllers = TaskController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
})
@AutoConfigureMockMvc(addFilters = false)
public class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createTask() throws Exception {
        TaskCreateDTO dto = new TaskCreateDTO();
        dto.setTitle("New Task");
        dto.setDeadline(LocalDateTime.now().plusDays(1));

        TaskResponseDTO response = new TaskResponseDTO();
        response.setId(1);
        response.setTitle("New Task");

        when(taskService.createTask(any(TaskCreateDTO.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Task"));

    }

    @Test
    void getTasksForUser() throws Exception {
        TaskSummaryDTO dto = new TaskSummaryDTO();
        dto.setTitle("First Task");
        dto.setId(1);

        TaskSummaryDTO dto2 = new TaskSummaryDTO();
        dto2.setTitle("Second Task");
        dto2.setId(2);

        when(taskService.getUserTasks()).thenReturn(List.of(dto, dto2));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("First Task"))
                .andExpect(jsonPath("$[1].title").value("Second Task"));

    }

    @Test
    void getTaskById() throws Exception {
        TaskResponseDTO response = new TaskResponseDTO();
        response.setId(1);
        response.setTitle("First Task");

        when(taskService.getTaskById(1)).thenReturn(response);

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First Task"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getTasksByKeywordInTitle() throws Exception {
        TaskSummaryDTO dto = new TaskSummaryDTO();
        dto.setId(1);
        dto.setTitle("First Task");

        when(taskService.findByTitle("first")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/tasks/search/title/first"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("First Task"));
    }

    @Test
    void getTasksByStatus() throws Exception {
        TaskSummaryDTO dto = new TaskSummaryDTO();
        dto.setId(1);
        dto.setStatus(Status.COMPLETED);

        when(taskService.findByStatus(Status.COMPLETED)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/tasks/search/status/COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void updateTask() throws Exception {
        TaskUpdateDTO dto = new TaskUpdateDTO();
        dto.setTitle("New Task");
        dto.setDeadline(LocalDateTime.now().plusDays(1));

        TaskResponseDTO response = new TaskResponseDTO();
        response.setId(1);
        response.setTitle("New Task");

        when(taskService.updateTask(eq(1), any(TaskUpdateDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/tasks/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("New Task"));
    }

    @Test
    void deleteTask() throws Exception {
        doNothing().when(taskService).deleteTask(1);

        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());

        verify(taskService).deleteTask(1);
    }

}
