package com.coloop.agent.capability.command;

import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;

/**
 * 压缩命令：/compact — 清空当前消息历史，释放上下文窗口。
 *
 * <p>当前实现为直接重置会话（与 /new 行为一致）。
 * 未来可升级为摘要保留：将历史消息压缩为摘要后再注入系统提示。</p>
 */
public class CompactCommand implements Command {

    @Override
    public String getName() {
        return "compact";
    }

    @Override
    public String getDescription() {
        return "Compact conversation context by clearing message history";
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        if (ctx.getAgentLoop() != null) {
            ctx.getAgentLoop().reset();
        }
        return CommandResult.success("Context compacted. Previous messages cleared.");
    }
}
