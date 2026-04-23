package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandResult;

/**
 * 帮助命令：/help — 列出所有可用命令。
 */
public class HelpCommand implements Command {

    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Show available commands";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        StringBuilder sb = new StringBuilder("Available commands:\n");
        for (Command cmd : registry.getAll()) {
            sb.append("  /").append(cmd.getName())
              .append(" - ").append(cmd.getDescription())
              .append("\n");
        }
        return CommandResult.success(sb.toString().trim());
    }
}
