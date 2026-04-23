package com.coloop.agent.core.command;

/**
 * 命令接口：定义一个可执行的斜杠命令（如 /help、/exit）。
 *
 * <p>命令通过 {@link CommandRegistry} 动态注册，由 {@link com.coloop.agent.capability.command.CommandInterceptor}
 * 在 AgentLoop 调用 LLM 前拦截并执行。</p>
 */
public interface Command {

    /**
     * 命令名称（不含前导斜杠），如 "help"。
     */
    String getName();

    /**
     * 命令简短描述，用于 /help 列表展示。
     */
    String getDescription();

    /**
     * 执行命令。
     *
     * @param ctx  执行上下文，包含 AgentLoop、AppConfig 等
     * @param args 命令参数（命令名后的剩余字符串，可能为空）
     * @return 命令执行结果
     */
    CommandResult execute(CommandContext ctx, String args);
}
