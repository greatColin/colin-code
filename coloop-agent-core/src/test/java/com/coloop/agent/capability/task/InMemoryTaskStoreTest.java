package com.coloop.agent.capability.task;

import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryTaskStoreTest {

    @Test
    public void testSaveAndFind() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task task = new Task();
        task.setId("t-1");
        task.setSubject("Test");
        task.setStatus(TaskStatus.PENDING);

        store.save(task);

        Task found = store.findById("t-1");
        assertNotNull(found);
        assertEquals("Test", found.getSubject());
    }

    @Test
    public void testFindAll() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task t1 = new Task(); t1.setId("t-1"); t1.setStatus(TaskStatus.PENDING);
        Task t2 = new Task(); t2.setId("t-2"); t2.setStatus(TaskStatus.PENDING);
        store.save(t1);
        store.save(t2);

        List<Task> all = store.findAll();
        assertEquals(2, all.size());
    }

    @Test
    public void testDelete() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        Task t1 = new Task(); t1.setId("t-1"); t1.setStatus(TaskStatus.PENDING);
        store.save(t1);

        store.delete("t-1");

        assertNull(store.findById("t-1"));
        assertTrue(store.findAll().isEmpty());
    }
}
