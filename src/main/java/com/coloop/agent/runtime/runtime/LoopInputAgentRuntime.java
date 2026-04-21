package com.coloop.agent.runtime.runtime;

import com.coloop.agent.core.agent.AgentLoop;

import java.util.Scanner;

/**
 * 循环获取用户输入，调用循环
 */
public class LoopInputAgentRuntime {
    private final AgentLoop agentLoop;

    public LoopInputAgentRuntime(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    public String chat() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("question: ");
            String input = scanner.nextLine();
            input = input.trim();
            if(input.isEmpty()) {
                continue;
            }
            if("/exit".equals(input)) {
                return "";
            }
            agentLoop.chat(input);
        }
    }
}
