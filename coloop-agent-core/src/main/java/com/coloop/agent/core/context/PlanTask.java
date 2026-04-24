package com.coloop.agent.core.context;

/**
 * Plan 模式中的单个任务项。
 */
public class PlanTask {

    public enum Status {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }

    private final int id;
    private final String description;
    private volatile Status status;

    public PlanTask(int id, String description) {
        this.id = id;
        this.description = description;
        this.status = Status.PENDING;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
