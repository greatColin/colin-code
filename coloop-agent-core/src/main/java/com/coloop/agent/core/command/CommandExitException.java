package com.coloop.agent.core.command;

/**
 * 命令退出异常：当命令要求终止会话时抛出。
 *
 * <p>由 {@link com.coloop.agent.capability.command.CommandInterceptor} 在检测到
 * {@link CommandResult#shouldTerminate()} 为 true 时抛出，供外层运行时
 *（如 {@link com.coloop.agent.core.agent.AgentLoopThread}）捕获并优雅退出。</p>
 */
public class CommandExitException extends RuntimeException {

    private final String exitMessage;

    public CommandExitException(String exitMessage) {
        super(exitMessage);
        this.exitMessage = exitMessage;
    }

    public String getExitMessage() {
        return exitMessage;
    }
}
