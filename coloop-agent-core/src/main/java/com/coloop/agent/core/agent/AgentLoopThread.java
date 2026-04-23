package com.coloop.agent.core.agent;

import com.coloop.agent.core.command.CommandExitException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * AgentLoop 的线程包装器，支持主/子两种运行模式。
 *
 * <p>将 AgentLoop 的同步阻塞调用放到独立线程中执行，
 * 通过 {@link #submit(String)} 提交消息、{@link #takeResult()} 获取结果，
 * 实现与非阻塞输入源的解耦。</p>
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>{@link Mode#MAIN} — 主模式：无限循环处理消息，直到收到 {@code /exit} 或被 {@link #stop()}。</li>
 *   <li>{@link Mode#SUB} — 子模式：执行一次对话，完成后自动结束。</li>
 * </ul>
 *
 * <p>中间状态（思考过程、工具调用等）仍通过 {@link AgentHook} 输出；
 * 最终结果通过 {@link #resultQueue} 返回。</p>
 */
public class AgentLoopThread {

    /** 运行模式 */
    public enum Mode {
        /** 主模式：无限循环，处理多条消息 */
        MAIN,
        /** 子模式：单次执行，完成后自动结束 */
        SUB
    }

    private final AgentLoop agentLoop;
    private final Mode mode;
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> resultQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private Thread thread;

    public AgentLoopThread(AgentLoop agentLoop, Mode mode) {
        this.agentLoop = agentLoop;
        this.mode = mode;
    }

    /** 启动线程 */
    public void start() {
        running = true;
        thread = new Thread(this::runLoop, "AgentThread-" + mode.name());
        thread.start();
    }

    /** 提交用户消息 */
    public void submit(String message) {
        inputQueue.offer(message);
    }

    /** 非阻塞获取结果，没有则返回 null */
    public String pollResult() {
        return resultQueue.poll();
    }

    /** 阻塞获取结果 */
    public String takeResult() throws InterruptedException {
        return resultQueue.take();
    }

    /** 停止线程 */
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** 等待线程结束 */
    public void join() throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
    }

    /** 是否仍在运行 */
    public boolean isRunning() {
        return running && thread != null && thread.isAlive();
    }

    /** 获取底层 AgentLoop（用于注入消息或重置上下文） */
    public AgentLoop getAgentLoop() {
        return agentLoop;
    }

    private void runLoop() {
        try {
            if (mode == Mode.SUB) {
                runSubMode();
            } else {
                runMainMode();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
    }

    private void runSubMode() throws InterruptedException {
        String input = inputQueue.take();
        try {
            String result = agentLoop.chat(input);
            resultQueue.offer(result);
        } catch (Exception e) {
            resultQueue.offer("Error: " + e.getMessage());
        }
    }

    private void runMainMode() throws InterruptedException {
        while (running && !Thread.interrupted()) {
            String input = inputQueue.take();

            try {
                String result = agentLoop.chat(input);
                resultQueue.offer(result);
            } catch (CommandExitException e) {
                resultQueue.offer(e.getExitMessage());
                running = false;
                break;
            } catch (Exception e) {
                resultQueue.offer("Error: " + e.getMessage());
            }
        }
    }
}
