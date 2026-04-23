package com.coloop.agent.capability.command;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.Command;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandResult;

/**
 * Markdown 提示词命令：将模板内容渲染后作为用户消息发送给 LLM。
 *
 * <p>执行时替换模板中的 {@code $ARGUMENTS} 为命令参数，然后通过
 * {@link CommandContext#getAgentLoop()} 调用 LLM。替换后的内容不以 {@code /}
 * 开头，不会触发命令拦截器的递归拦截。</p>
 */
public class MdPromptCommand implements Command {

    private final String name;
    private final String description;
    private final String promptTemplate;

    public MdPromptCommand(String name, String description, String promptTemplate) {
        this.name = name;
        this.description = description;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public CommandResult execute(CommandContext ctx, String args) {
        String rendered = render(args);
        AgentLoop loop = ctx.getAgentLoop();
        if (loop != null) {
            String response = loop.chat(rendered);
            return CommandResult.success(response);
        }
        return CommandResult.success(rendered);
    }

    private String render(String args) {
        return promptTemplate.replace("$ARGUMENTS", args != null ? args : "");
    }
}
