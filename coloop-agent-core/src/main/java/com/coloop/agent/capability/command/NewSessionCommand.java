package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;

/**
 * 新会话命令：/new — 重置当前会话上下文。
 *
 * <p>调用 {@link CommandContext#getAgentLoop()}#reset() 清空消息历史。
 * 对于需要完全重建 AgentLoop 的运行时（如 WebSocket），可通过
 * {@link CommandContext#setAttribute(String, Object)} 注册一个 {@code resetSession}
 * 回调（Runnable）来执行额外清理。</p>
 */
public class NewSessionCommand implements Command {

    @Override
    public String getName() {
        return "new";
    }

    @Override
    public String getDescription() {
        return "Start a new session, clearing all context";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        if (ctx.getAgentLoop() != null) {
            ctx.getAgentLoop().reset();
        }

        Object resetter = ctx.getAttribute("resetSession");
        if (resetter instanceof Runnable) {
            ((Runnable) resetter).run();
        }

        return CommandResult.success("New session started. Previous context cleared.");
    }
}
