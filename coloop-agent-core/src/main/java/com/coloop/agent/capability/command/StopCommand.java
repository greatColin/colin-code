package com.coloop.agent.capability.command;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;

/**
 * 停止命令：/stop — 中断当前正在执行的 Agent 循环。
 *
 * <p>在 LLM 迭代或工具调用过程中发送停止信号，循环将在下一轮检查点安全退出。
 * 适用于长时间运行或无限工具调用链的场景。</p>
 */
public class StopCommand implements Command {

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getDescription() {
        return "Stop the currently running agent loop";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        AgentLoop loop = ctx.getAgentLoop();
        if (loop == null) {
            return CommandResult.success("No active loop to stop.");
        }
        if (loop.isStopRequested()) {
            return CommandResult.success("Stop already requested.");
        }
        loop.requestStop();
        return CommandResult.success("Stop requested. The loop will exit at the next safe checkpoint.");
    }
}
