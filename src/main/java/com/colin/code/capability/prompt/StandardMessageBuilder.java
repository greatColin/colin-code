package com.colin.code.capability.prompt;

import com.colin.code.core.message.MessageBuilder;
import com.colin.code.core.prompt.PromptPlugin;
import com.colin.code.core.provider.LLMResponse;
import com.colin.code.core.provider.ToolCallRequest;
import com.colin.code.runtime.config.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardMessageBuilder implements MessageBuilder {

    private final List<PromptPlugin> plugins;
    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StandardMessageBuilder(List<PromptPlugin> plugins, AppConfig config) {
        this.plugins = plugins.stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .collect(Collectors.toList());
        this.config = config;
    }

    @Override
    public List<Map<String, Object>> buildInitial(String userMessage) {
        Map<String, Object> runtimeContext = new HashMap<>();
        runtimeContext.put("time", java.time.ZonedDateTime.now().toString());
        runtimeContext.put("cwd", java.nio.file.Paths.get(".").toAbsolutePath().normalize().toString());
        runtimeContext.put("os", System.getProperty("os.name"));

        List<String> parts = new ArrayList<>();
        for (PromptPlugin plugin : plugins) {
            String generated = plugin.generate(config, runtimeContext);
            if (generated != null && !generated.isEmpty()) {
                parts.add(generated);
            }
        }

        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", String.join("\n\n", parts));

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage != null ? userMessage : "");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMsg);
        messages.add(userMsg);
        return messages;
    }

    @Override
    public void addAssistantMessage(List<Map<String, Object>> messages, LLMResponse response) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "assistant");
        msg.put("content", response.getContent() != null ? response.getContent() : "");
        if (response.hasToolCalls()) {
            msg.put("tool_calls", wrapToolCalls(response.getToolCalls()));
        }
        messages.add(msg);
    }

    @Override
    public void addToolResult(List<Map<String, Object>> messages, ToolCallRequest tc, String result) {
        Map<String, Object> tr = new HashMap<>();
        tr.put("role", "tool");
        tr.put("tool_call_id", tc.getId());
        tr.put("content", result != null ? result : "");
        messages.add(tr);
    }

    private List<Map<String, Object>> wrapToolCalls(List<ToolCallRequest> toolCalls) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolCallRequest tc : toolCalls) {
            Map<String, Object> fn = new HashMap<>();
            fn.put("id", tc.getId());
            fn.put("type", "function");
            Map<String, Object> f = new HashMap<>();
            f.put("name", tc.getName());
            try {
                f.put("arguments", objectMapper.writeValueAsString(tc.getArguments()));
            } catch (Exception e) {
                f.put("arguments", "{}");
            }
            fn.put("function", f);
            out.add(fn);
        }
        return out;
    }
}
