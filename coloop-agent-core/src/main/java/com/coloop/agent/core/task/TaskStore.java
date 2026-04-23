package com.coloop.agent.core.task;

import java.util.List;

public interface TaskStore {
    Task save(Task task);
    Task findById(String id);
    List<Task> findAll();
    void delete(String id);
}
