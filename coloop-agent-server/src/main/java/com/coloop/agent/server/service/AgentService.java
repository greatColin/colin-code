package com.coloop.agent.server.service;

import com.coloop.agent.capability.provider.openai.OpenAICompatibleProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.StandardCapability;
import com.coloop.agent.runtime.config.AppConfig;
import com.coloop.agent.server.hook.WebSocketLoggingHook;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentService {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<>();

    private static class SessionContext {
        AgentLoop agentLoop;
        boolean isRunning;
    }

    public void startChat(String userMessage, WebSocketSession session) {
        SessionContext ctx = sessions.computeIfAbsent(session.getId(), k -> new SessionContext());

        String trimmed = userMessage.trim();

        // 处理 /new-session 命令
        if ("/new-session".equals(trimmed)) {
            synchronized (ctx) {
                if (ctx.isRunning) {
                    sendSystem(session, "A task is currently running. Please wait for it to complete before starting a new session.");
                    return;
                }
                if (ctx.agentLoop != null) {
                    ctx.agentLoop.reset();
                }
                ctx.agentLoop = null;
                sendSystem(session, "New session started. Previous context cleared.");
                return;
            }
        }

        synchronized (ctx) {
            if (ctx.isRunning && ctx.agentLoop != null) {
                ctx.agentLoop.injectUserMessage(userMessage);
                return;
            }
            ctx.isRunning = true;
        }

        executor.submit(() -> {
            try {
                AgentLoop agentLoop;
                synchronized (ctx) {
                    if (ctx.agentLoop == null) {
                        AppConfig config = AppConfig.fromSetting("coloop-agent-setting.json");
                        LLMProvider provider = new OpenAICompatibleProvider(config.getModelConfig("minimax"));
                        WebSocketLoggingHook hook = new WebSocketLoggingHook(session);

                        agentLoop = new CapabilityLoader()
                                .withCapability(StandardCapability.EXEC_TOOL, config)
                                .withCapability(StandardCapability.READ_FILE_TOOL, config)
                                .withCapability(StandardCapability.WRITE_FILE_TOOL, config)
                                .withCapability(StandardCapability.EDIT_FILE_TOOL, config)
                                .withCapability(StandardCapability.SEARCH_FILES_TOOL, config)
                                .withCapability(StandardCapability.LIST_DIRECTORY_TOOL, config)
                                .withCapability(StandardCapability.BASE_PROMPT, config)
                                .withCapability(StandardCapability.AGENTS_MD_PROMPT, config)
                                .withCapability(StandardCapability.LOGGING_HOOK, config)
                                .withHook(hook)
                                .build(provider, config);

                        ctx.agentLoop = agentLoop;
                    } else {
                        agentLoop = ctx.agentLoop;
                    }
                }

                agentLoop.chat(userMessage);
            } catch (Exception e) {
                sendError(session, e.getMessage());
            } finally {
                synchronized (ctx) {
                    ctx.isRunning = false;
                }
            }
        });
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            WebSocketMessage errorMsg = WebSocketMessage.error(message);
            String json = objectMapper.writeValueAsString(errorMsg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("Failed to send error message: " + e.getMessage());
        }
    }

    private void sendSystem(WebSocketSession session, String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            WebSocketMessage systemMsg = WebSocketMessage.system(message);
            String json = objectMapper.writeValueAsString(systemMsg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("Failed to send system message: " + e.getMessage());
        }
    }
}
