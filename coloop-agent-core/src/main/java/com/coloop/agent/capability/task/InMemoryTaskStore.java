package com.coloop.agent.capability.task;

import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTaskStore implements TaskStore {

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public Task save(Task task) {
        tasks.put(task.getId(), task);
        return task;
    }

    @Override
    public Task findById(String id) {
        return tasks.get(id);
    }

    @Override
    public List<Task> findAll() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public void delete(String id) {
        tasks.remove(id);
    }
}
