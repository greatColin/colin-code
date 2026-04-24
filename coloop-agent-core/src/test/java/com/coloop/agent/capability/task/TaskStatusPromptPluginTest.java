package com.coloop.agent.capability.task;

import com.coloop.agent.core.task.TaskStatus;
import com.coloop.agent.runtime.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TaskStatusPromptPluginTest {

    @Test
    public void testReturnsNullWhenNoTasks() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        TaskStatusPromptPlugin plugin = new TaskStatusPromptPlugin(service);

        String result = plugin.generate(new AppConfig(), Map.of());

        assertNull(result);
    }

    @Test
    public void testIncludesGuideAndActiveTasks() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        var task = service.create("读取配置", "");
        service.update(task.getId(), null, null, TaskStatus.IN_PROGRESS, null);

        TaskStatusPromptPlugin plugin = new TaskStatusPromptPlugin(service);
        String result = plugin.generate(new AppConfig(), Map.of());

        assertNotNull(result);
        assertTrue(result.contains("任务管理"));
        assertTrue(result.contains("task_create"));
        assertTrue(result.contains("[IN_PROGRESS]"));
        assertTrue(result.contains("读取配置"));
    }

    @Test
    public void testFoldsCompletedTasks() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        for (int i = 0; i < 5; i++) {
            var t = service.create("任务" + i, "");
            service.update(t.getId(), null, null, TaskStatus.COMPLETED, null);
        }

        TaskStatusPromptPlugin plugin = new TaskStatusPromptPlugin(service);
        String result = plugin.generate(new AppConfig(), Map.of());

        assertTrue(result.contains("...及 5 个已完成任务"));
    }
}
