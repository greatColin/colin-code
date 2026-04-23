package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;

/**
 * 退出命令：/exit — 终止当前会话。
 */
public class ExitCommand implements Command {

    @Override
    public String getName() {
        return "exit";
    }

    @Override
    public String getDescription() {
        return "Exit the current session";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        return CommandResult.terminate("Exited");
    }
}
