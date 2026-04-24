package com.coloop.agent.core.context;

/**
 * 会话状态：跨组件共享的可变会话数据。
 *
 * <p>用于解耦 {@link com.coloop.agent.core.agent.AgentLoop} 与
 * {@link com.coloop.agent.core.prompt.PromptPlugin} 之间的摘要传递，
 * 避免循环依赖。</p>
 */
public class ConversationState {

    private volatile ConversationSummary summary;
    private volatile String pendingPlan;
    private volatile String planRequest;
    private volatile java.util.List<PlanTask> planTasks;

    public void setSummary(ConversationSummary summary) {
        this.summary = summary;
    }

    public ConversationSummary getSummary() {
        return summary;
    }

    public void setPendingPlan(String pendingPlan) {
        this.pendingPlan = pendingPlan;
    }

    public String getPendingPlan() {
        return pendingPlan;
    }

    public void setPlanRequest(String planRequest) {
        this.planRequest = planRequest;
    }

    public String getPlanRequest() {
        return planRequest;
    }

    public void setPlanTasks(java.util.List<PlanTask> planTasks) {
        this.planTasks = planTasks;
    }

    public java.util.List<PlanTask> getPlanTasks() {
        return planTasks;
    }

    /** 重置所有 Plan 相关状态。 */
    public void clearPlan() {
        this.pendingPlan = null;
        this.planRequest = null;
        this.planTasks = null;
    }
}
