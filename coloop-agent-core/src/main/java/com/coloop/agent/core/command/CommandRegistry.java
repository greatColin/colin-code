package com.coloop.agent.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令注册表：动态管理所有 {@link Command} 实例。
 *
 * <p>支持注册内置命令和用户自定义命令（如从 {@code ~/.coloop/commands/} 扫描加载）。</p>
 */
public class CommandRegistry {

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /**
     * 注册一个命令。若同名命令已存在则覆盖。
     */
    public void register(Command command) {
        commands.put(command.getName(), command);
    }

    /**
     * 根据命令名查找命令。
     *
     * @param name 命令名（不含前导斜杠）
     * @return 命令实例，若未找到返回 null
     */
    public Command get(String name) {
        return commands.get(name);
    }

    /**
     * 获取所有已注册命令（按注册顺序）。
     */
    public List<Command> getAll() {
        return new ArrayList<>(commands.values());
    }

    /**
     * 检查是否存在指定命令。
     */
    public boolean hasCommand(String name) {
        return commands.containsKey(name);
    }
}
