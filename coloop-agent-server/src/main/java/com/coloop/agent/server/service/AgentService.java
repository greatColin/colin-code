package com.coloop.agent.server.service;

import com.coloop.agent.capability.command.CommandInterceptor;
import com.coloop.agent.capability.command.CommandScanner;
import com.coloop.agent.capability.command.CompactCommand;
import com.coloop.agent.capability.command.ExitCommand;
import com.coloop.agent.capability.command.HelpCommand;
import com.coloop.agent.capability.command.ModelCommand;
import com.coloop.agent.capability.command.NewSessionCommand;
import com.coloop.agent.capability.provider.openai.OpenAICompatibleProvider;
import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.command.CommandContext;
import com.coloop.agent.core.command.CommandRegistry;
import com.coloop.agent.core.command.CommandExitException;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.runtime.CapabilityLoader;
import com.coloop.agent.runtime.StandardCapability;
import com.coloop.agent.runtime.config.AppConfig;
import com.coloop.agent.core.command.Command;
import com.coloop.agent.server.hook.WebSocketLoggingHook;
import com.coloop.agent.server.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        synchronized (ctx) {
            if (ctx.isRunning && ctx.agentLoop != null) {
                // 任务运行时拒绝命令（斜杠开头），普通消息注入当前循环
                if (trimmed.startsWith("/")) {
                    sendSystem(session, "A task is currently running. Please wait for it to complete before executing commands.");
                    return;
                }
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
                        LLMProvider provider = new OpenAICompatibleProvider(config.getDefaultModelConfig());
                        WebSocketLoggingHook hook = new WebSocketLoggingHook(session);

                        // 组装命令系统
                        CommandRegistry cmdRegistry = new CommandRegistry();
                        cmdRegistry.register(new ExitCommand());
                        cmdRegistry.register(new NewSessionCommand());
                        cmdRegistry.register(new CompactCommand());
                        cmdRegistry.register(new ModelCommand());
                        cmdRegistry.register(new HelpCommand(cmdRegistry));
                        CommandScanner.scanUserCommands(cmdRegistry);
                        CommandScanner.scanProjectCommands(cmdRegistry);

                        // 创建任务管理能力，提取 /tasks 命令注册到命令系统
                        com.coloop.agent.capability.task.TaskManagementCapability taskCap =
                                new com.coloop.agent.capability.task.TaskManagementCapability(config);
                        cmdRegistry.register(taskCap.getTasksCommand());

                        // TODO: Plan Mode 暂时下线（稳定性问题），恢复时取消注释以下 4 行
                        // 创建 Plan Mode 能力
                        // com.coloop.agent.capability.plan.PlanCapability planCap =
                        //         new com.coloop.agent.capability.plan.PlanCapability(provider, config);
                        // cmdRegistry.register(planCap.getPlanCommand());
                        // cmdRegistry.register(planCap.getCancelCommand());

                        // 将 TaskService 注入 WebSocketLoggingHook，使其能推送任务列表
                        hook.setTaskService(taskCap.getTaskService());

                        CommandContext cmdCtx = new CommandContext(config, null);
                        cmdCtx.setAttribute("session", session);
                        cmdCtx.setAttribute("streamChunkSender", (java.util.function.Consumer<String>) chunk -> {
                            if (!session.isOpen()) return;
                            try {
                                WebSocketMessage msg = WebSocketMessage.streamChunk(chunk);
                                String json = objectMapper.writeValueAsString(msg);
                                session.sendMessage(new TextMessage(json));
                            } catch (Exception e) {
                                System.err.println("Failed to send plan stream chunk: " + e.getMessage());
                            }
                        });
                        cmdCtx.setAttribute("sendTaskList", (java.util.function.Consumer<java.util.List<java.util.Map<String, Object>>>) tasks -> {
                            if (!session.isOpen()) return;
                            try {
                                WebSocketMessage msg = WebSocketMessage.taskList(tasks);
                                String json = objectMapper.writeValueAsString(msg);
                                session.sendMessage(new TextMessage(json));
                            } catch (Exception e) {
                                System.err.println("Failed to send task list: " + e.getMessage());
                            }
                        });
                        cmdCtx.setAttribute("sendTaskUpdate", (java.util.function.Consumer<java.util.Map<String, Object>>) update -> {
                            if (!session.isOpen()) return;
                            try {
                                WebSocketMessage msg = WebSocketMessage.taskUpdate(
                                        (Integer) update.get("id"),
                                        (String) update.get("status"),
                                        (String) update.get("description")
                                );
                                String json = objectMapper.writeValueAsString(msg);
                                session.sendMessage(new TextMessage(json));
                            } catch (Exception e) {
                                System.err.println("Failed to send task update: " + e.getMessage());
                            }
                        });
                        cmdCtx.setAttribute("resetSession", (Runnable) () -> {
                            synchronized (ctx) {
                                ctx.agentLoop = null;
                            }
                        });

                        CommandInterceptor cmdInterceptor = new CommandInterceptor(cmdRegistry, cmdCtx);

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
                                .withCapability(StandardCapability.SUMMARY_PROMPT, config)
                                .withCapability(StandardCapability.MCP_CLIENT, config)
                                .withComposite(taskCap)
                                // TODO: 恢复 Plan Mode 时取消注释 .withComposite(planCap)
                                // .withComposite(planCap)
                                .withHook(hook)
                                .withInterceptor(cmdInterceptor)
                                .build(provider, config);

                        hook.setAgentLoop(agentLoop);

                        cmdCtx.setAgentLoop(agentLoop);
                        ctx.agentLoop = agentLoop;
                    } else {
                        agentLoop = ctx.agentLoop;
                    }
                }

                agentLoop.chatStream(userMessage, new LLMProvider.StreamConsumer() {
                    @Override
                    public void onContent(String chunk) {
                        // chunk 已通过 Hook 推送到前端，此处无需额外操作
                    }

                    @Override
                    public void onToolCall(ToolCallRequest toolCall) {
                        // tool call 已通过 Hook 推送到前端
                    }

                    @Override
                    public void onComplete(LLMResponse response) {
                        // 完成，Hook 的 onLoopEnd 已发送 assistant 消息
                    }

                    @Override
                    public void onError(String error) {
                        sendError(session, error);
                    }
                });
            } catch (CommandExitException e) {
                sendSystem(session, e.getExitMessage());
                synchronized (ctx) {
                    ctx.agentLoop = null;
                }
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

    /**
     * 发送可用命令列表到前端（WebSocket 连接建立时调用）。
     */
    public void sendAvailableCommands(WebSocketSession session) {
        CommandRegistry listRegistry = new CommandRegistry();
        listRegistry.register(new ExitCommand());
        listRegistry.register(new NewSessionCommand());
        listRegistry.register(new CompactCommand());
        listRegistry.register(new ModelCommand());
        listRegistry.register(new HelpCommand(listRegistry));
        CommandScanner.scanUserCommands(listRegistry);
        CommandScanner.scanProjectCommands(listRegistry);

        // TODO: 恢复 Plan Mode 时取消注释以下 2 行
        // listRegistry.register(new com.coloop.agent.capability.plan.PlanCommand(null, new AppConfig(), new com.coloop.agent.core.context.ConversationState()));
        // listRegistry.register(new com.coloop.agent.capability.plan.CancelCommand(new com.coloop.agent.core.context.ConversationState()));

        List<Map<String, String>> commands = new ArrayList<>();
        for (Command cmd : listRegistry.getAll()) {
            Map<String, String> entry = new HashMap<>();
            entry.put("name", cmd.getName());
            entry.put("description", cmd.getDescription());
            commands.add(entry);
        }

        try {
            WebSocketMessage msg = WebSocketMessage.commands(commands);
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            System.err.println("Failed to send command list: " + e.getMessage());
        }
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
