package com.coloop.agent.capability.task;

import com.coloop.agent.core.task.Task;
import com.coloop.agent.core.task.TaskStatus;
import com.coloop.agent.core.task.TaskStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskService {

    private final TaskStore store;

    public TaskService(TaskStore store) {
        this.store = store;
    }

    public Task create(String subject, String description) {
        Task task = new Task();
        task.setId(generateId());
        task.setSubject(subject);
        task.setDescription(description != null ? description : "");
        task.setStatus(TaskStatus.PENDING);
        task.setBlockedBy(new ArrayList<>());
        task.setBlocks(new ArrayList<>());
        long now = System.currentTimeMillis();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return store.save(task);
    }

    public Task get(String id) {
        return store.findById(id);
    }

    public List<Task> list() {
        return store.findAll();
    }

    public Task update(String id, String subject, String description,
                       TaskStatus status, List<String> blockedBy) {
        Task task = store.findById(id);
        if (task == null) {
            return null;
        }

        if (subject != null) {
            task.setSubject(subject);
        }
        if (description != null) {
            task.setDescription(description);
        }

        if (status != null) {
            if (status == TaskStatus.IN_PROGRESS) {
                autoCompleteExistingInProgress(id);
            }
            task.setStatus(status);
        }

        if (blockedBy != null) {
            updateBlockedBy(task, blockedBy);
        }

        task.setUpdatedAt(System.currentTimeMillis());
        return store.save(task);
    }

    public void delete(String id) {
        Task task = store.findById(id);
        if (task == null) return;

        for (String blockedId : new ArrayList<>(task.getBlocks())) {
            Task blocked = store.findById(blockedId);
            if (blocked != null) {
                blocked.getBlockedBy().remove(id);
                store.save(blocked);
            }
        }

        for (String depId : new ArrayList<>(task.getBlockedBy())) {
            Task dep = store.findById(depId);
            if (dep != null) {
                dep.getBlocks().remove(id);
                store.save(dep);
            }
        }

        store.delete(id);
    }

    private void autoCompleteExistingInProgress(String excludeId) {
        for (Task t : store.findAll()) {
            if (t.getStatus() == TaskStatus.IN_PROGRESS && !t.getId().equals(excludeId)) {
                t.setStatus(TaskStatus.COMPLETED);
                t.setUpdatedAt(System.currentTimeMillis());
                store.save(t);
            }
        }
    }

    private void updateBlockedBy(Task task, List<String> newBlockedBy) {
        String id = task.getId();

        for (String oldId : new ArrayList<>(task.getBlockedBy())) {
            Task oldDep = store.findById(oldId);
            if (oldDep != null) {
                oldDep.getBlocks().remove(id);
                store.save(oldDep);
            }
        }

        for (String newId : newBlockedBy) {
            Task newDep = store.findById(newId);
            if (newDep != null) {
                newDep.getBlocks().add(id);
                store.save(newDep);
            }
        }

        task.setBlockedBy(new ArrayList<>(newBlockedBy));
    }

    private String generateId() {
        return "t-" + UUID.randomUUID().toString().substring(0, 4);
    }
}
