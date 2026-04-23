package com.coloop.agent.capability.task;

import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TaskServiceTest {

    @Test
    public void testCreateAssignsPendingAndId() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task task = service.create("读取配置", "读取配置文件内容");

        assertNotNull(task.getId());
        assertTrue(task.getId().startsWith("t-"));
        assertEquals("读取配置", task.getSubject());
        assertEquals("读取配置文件内容", task.getDescription());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertNotNull(task.getBlockedBy());
        assertTrue(task.getBlockedBy().isEmpty());
    }

    @Test
    public void testAutoCompleteOldInProgress() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task task1 = service.create("任务1", "");
        Task task2 = service.create("任务2", "");

        service.update(task1.getId(), null, null, TaskStatus.IN_PROGRESS, null);
        service.update(task2.getId(), null, null, TaskStatus.IN_PROGRESS, null);

        Task updated1 = service.get(task1.getId());
        assertEquals(TaskStatus.COMPLETED, updated1.getStatus());

        Task updated2 = service.get(task2.getId());
        assertEquals(TaskStatus.IN_PROGRESS, updated2.getStatus());
    }

    @Test
    public void testBlockedByBidirectional() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task taskA = service.create("任务A", "");
        Task taskB = service.create("任务B", "");

        service.update(taskB.getId(), null, null, null,
                Collections.singletonList(taskA.getId()));

        Task updatedA = service.get(taskA.getId());
        assertTrue(updatedA.getBlocks().contains(taskB.getId()));

        Task updatedB = service.get(taskB.getId());
        assertTrue(updatedB.getBlockedBy().contains(taskA.getId()));
    }

    @Test
    public void testBlockedByReplacementClearsOld() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task taskA = service.create("任务A", "");
        Task taskB = service.create("任务B", "");
        Task taskC = service.create("任务C", "");

        service.update(taskC.getId(), null, null, null,
                Collections.singletonList(taskA.getId()));
        service.update(taskC.getId(), null, null, null,
                Collections.singletonList(taskB.getId()));

        Task updatedA = service.get(taskA.getId());
        assertFalse(updatedA.getBlocks().contains(taskC.getId()));

        Task updatedB = service.get(taskB.getId());
        assertTrue(updatedB.getBlocks().contains(taskC.getId()));
    }

    @Test
    public void testDeleteRemovesBidirectionalLinks() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        Task taskA = service.create("任务A", "");
        Task taskB = service.create("任务B", "");

        service.update(taskB.getId(), null, null, null,
                Collections.singletonList(taskA.getId()));
        service.delete(taskA.getId());

        Task updatedB = service.get(taskB.getId());
        assertTrue(updatedB.getBlockedBy().isEmpty());
    }
}
