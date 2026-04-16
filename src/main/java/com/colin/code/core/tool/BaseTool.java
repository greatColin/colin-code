package com.colin.code.core.tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具抽象基类：提供 OpenAI function 格式的 toSchema 默认实现。
 */
public abstract class BaseTool implements Tool {

    public Map<String, Object> toSchema() {
        Map<String, Object> function = new HashMap<>();
        function.put("name", getName());
        function.put("description", getDescription());
        function.put("parameters", getParameters());
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }
}
