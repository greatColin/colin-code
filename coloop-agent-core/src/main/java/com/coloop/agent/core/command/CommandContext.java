package com.coloop.agent.core.command;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令执行上下文。
 *
 * <p>为 {@link Command} 提供执行所需的运行时依赖：AgentLoop 实例、配置、
 * 终止回调，以及可扩展的属性字典（用于 WebSocket Session 等特定运行时对象）。</p>
 */
public class CommandContext {

    private AgentLoop agentLoop;
    private final AppConfig appConfig;
    private Runnable terminator;
    private final Map<String, Object> attributes = new HashMap<>();
    private volatile boolean terminated = false;

    /**
     * @param appConfig  应用配置
     * @param terminator 终止回调（如 /exit 时调用），可为 null 稍后通过 setter 设置
     */
    public CommandContext(AppConfig appConfig, Runnable terminator) {
        this.appConfig = appConfig;
        this.terminator = terminator;
    }

    public CommandContext(AppConfig appConfig) {
        this(appConfig, null);
    }

    /**
     * 设置 AgentLoop 引用（解决构建时的循环依赖）。
     */
    public void setAgentLoop(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    public void setTerminator(Runnable terminator) {
        this.terminator = terminator;
    }

    public AgentLoop getAgentLoop() {
        return agentLoop;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    /**
     * 触发终止信号。
     */
    public void terminate() {
        this.terminated = true;
        if (terminator != null) {
            terminator.run();
        }
    }

    public boolean isTerminated() {
        return terminated;
    }

    /**
     * 设置扩展属性。
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取扩展属性。
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
}
