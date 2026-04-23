package com.coloop.agent.runtime.runtime;

import com.coloop.agent.core.agent.AgentLoop;
import com.coloop.agent.core.agent.AgentLoopThread;

import java.util.Scanner;

/**
 * 循环获取用户输入，通过 {@link AgentLoopThread} 主模式提交给 AgentLoop 处理。
 *
 * <p>每提交一条消息后阻塞等待结果返回，中间状态（思考、工具调用等）
 * 仍通过 {@link com.coloop.agent.core.agent.AgentHook} 输出到控制台。</p>
 */
public class LoopInputAgentRuntime {

    private final AgentLoopThread agentThread;

    public LoopInputAgentRuntime(AgentLoop agentLoop) {
        this.agentThread = new AgentLoopThread(agentLoop, AgentLoopThread.Mode.MAIN);
    }

    public String chat() {
        agentThread.start();
        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                if (!agentThread.isRunning()) {
                    break;
                }
                System.out.print("question: ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }
                agentThread.submit(input);
                // 阻塞等待本次结果（中间状态通过 AgentHook 输出）
                agentThread.takeResult();
                if (!agentThread.isRunning()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            agentThread.stop();
            scanner.close();
        }
        return "";
    }

    /** 停止输入循环和底层线程。 */
    public void stop() {
        agentThread.stop();
    }

    /** 获取底层 AgentLoopThread（用于命令系统的终止回调）。 */
    public AgentLoopThread getAgentThread() {
        return agentThread;
    }
}
