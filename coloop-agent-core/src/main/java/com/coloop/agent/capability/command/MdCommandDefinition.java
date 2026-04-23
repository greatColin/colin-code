package com.coloop.agent.capability.command;

/**
 * Markdown 命令定义：frontmatter 元数据 + 提示词模板内容。
 */
public record MdCommandDefinition(String name, String description, String promptTemplate) {
}
