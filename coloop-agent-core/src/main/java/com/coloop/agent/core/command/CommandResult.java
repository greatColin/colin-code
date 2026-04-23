package com.coloop.agent.core.command;

/**
 * 命令执行结果。
 *
 * <p>包含要展示给用户的消息，以及是否触发终止信号。</p>
 */
public class CommandResult {

    private final String message;
    private final boolean terminate;

    private CommandResult(String message, boolean terminate) {
        this.message = message;
        this.terminate = terminate;
    }

    /**
     * 创建一个普通成功结果。
     */
    public static CommandResult success(String message) {
        return new CommandResult(message, false);
    }

    /**
     * 创建一个终止信号结果（如 /exit）。
     */
    public static CommandResult terminate(String message) {
        return new CommandResult(message, true);
    }

    public String getMessage() {
        return message;
    }

    public boolean shouldTerminate() {
        return terminate;
    }
}
