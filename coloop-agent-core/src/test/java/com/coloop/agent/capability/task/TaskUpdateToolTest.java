package com.coloop.agent.capability.task;

import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TaskUpdateToolTest {

    @Test
    public void testUpdateStatusReturnsCompactString() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task task = service.create("任务", "");
        TaskUpdateTool tool = new TaskUpdateTool(service);

        String result = tool.execute(Map.of("id", task.getId(), "status", "IN_PROGRESS"));

        assertEquals("Updated task " + task.getId() + ": status=IN_PROGRESS", result);
    }

    @Test
    public void testUpdateAutoCompletesPrevious() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task task1 = service.create("任务1", "");
        Task task2 = service.create("任务2", "");
        TaskUpdateTool tool = new TaskUpdateTool(service);

        tool.execute(Map.of("id", task1.getId(), "status", "IN_PROGRESS"));
        tool.execute(Map.of("id", task2.getId(), "status", "IN_PROGRESS"));

        assertEquals(TaskStatus.COMPLETED, service.get(task1.getId()).getStatus());
        assertEquals(TaskStatus.IN_PROGRESS, service.get(task2.getId()).getStatus());
    }
}
