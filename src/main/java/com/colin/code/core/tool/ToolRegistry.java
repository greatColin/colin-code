package com.colin.code.core.tool;

import com.colin.code.core.provider.ToolCallRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表：管理所有可用工具，并向 LLM 输出 function 定义列表。
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        if (tool != null) {
            tools.put(tool.getName(), tool);
        }
    }

    /** 返回所有工具的 OpenAI function 定义 */
    public List<Map<String, Object>> getDefinitions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tool t : tools.values()) {
            if (t instanceof BaseTool) {
                out.add(((BaseTool) t).toSchema());
            } else {
                Map<String, Object> fn = new HashMap<>();
                fn.put("type", "function");
                Map<String, Object> f = new HashMap<>();
                f.put("name", t.getName());
                f.put("description", t.getDescription());
                f.put("parameters", t.getParameters());
                fn.put("function", f);
                out.add(fn);
            }
        }
        return out;
    }

    /** 执行指定工具调用 */
    public String execute(ToolCallRequest tc) {
        Tool tool = tools.get(tc.getName());
        if (tool == null) {
            return "[Error: tool not found: " + tc.getName() + "]";
        }
        try {
            return tool.execute(tc.getArguments() != null ? tc.getArguments() : Collections.<String, Object>emptyMap());
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }
}
