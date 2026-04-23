package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandExitException;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;
import com.coloop.agent.core.interceptor.InputInterceptor;

import java.util.Optional;

/**
 * 命令拦截器：将斜杠命令（如 /help）从 LLM 调用前拦截并直接执行。
 *
 * <p>实现 {@link InputInterceptor}，可被 {@link com.coloop.agent.runtime.CapabilityLoader}
 * 通过 {@code withInterceptor()} 链式组装到 AgentLoop 中。</p>
 */
public class CommandInterceptor implements InputInterceptor {

    private final CommandRegistry registry;
    private final CommandContext context;

    public CommandInterceptor(CommandRegistry registry, CommandContext context) {
        this.registry = registry;
        this.context = context;
    }

    @Override
    public Optional<String> intercept(String userMessage) {
        String trimmed = userMessage.trim();
        if (!trimmed.startsWith("/")) {
            return Optional.empty();
        }

        // 解析命令名和参数
        String withoutSlash = trimmed.substring(1);
        String cmdName;
        String args = "";
        int spaceIdx = withoutSlash.indexOf(' ');
        if (spaceIdx >= 0) {
            cmdName = withoutSlash.substring(0, spaceIdx);
            args = withoutSlash.substring(spaceIdx + 1).trim();
        } else {
            cmdName = withoutSlash;
        }

        Command command = registry.get(cmdName);
        if (command == null) {
            return Optional.of("Unknown command: /" + cmdName + ". Type /help for available commands.");
        }

        CommandResult result = command.execute(context, args);

        if (result.shouldTerminate()) {
            throw new CommandExitException(result.getMessage());
        }

        return Optional.of(result.getMessage());
    }
}
