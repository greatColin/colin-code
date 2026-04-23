package com.coloop.agent.capability.task;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TaskCreateToolTest {

    @Test
    public void testCreateReturnsCompactString() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        TaskCreateTool tool = new TaskCreateTool(service);

        String result = tool.execute(Map.of("subject", "读取配置", "description", "读取配置文件"));

        assertTrue(result.startsWith("Created task t-"));
        assertTrue(result.contains("读取配置"));
        assertTrue(result.contains("[PENDING]"));
    }

    @Test
    public void testCreateWithoutDescription() {
        TaskService service = new TaskService(new InMemoryTaskStore());
        TaskCreateTool tool = new TaskCreateTool(service);

        String result = tool.execute(Map.of("subject", "测试"));
        assertTrue(result.startsWith("Created task t-"));
    }
}
