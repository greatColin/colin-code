package com.coloop.agent.capability.hook;

import com.coloop.agent.core.agent.AgentHook;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Claude Code 风格的日志钩子。
 *
 * <p>提供美观的控制台输出，使用 ANSI 颜色区分不同类型的信息：
 * <ul>
 *   <li><b>用户输入</b> - 亮绿色</li>
 *   <li><b>循环开始</b> - 亮蓝色，带循环计数</li>
 *   <li><b>推理内容</b> - 紫色</li>
 *   <li><b>思考内容</b> - 青色</li>
 *   <li><b>工具调用</b> - 亮黄色，带参数预览</li>
 *   <li><b>最终输出</b> - 白色</li>
 * </ul>
 *
 * <p>示例输出：
 * <pre>
 * ╭──────────────────────────────────────────────────╮
 * │ [ USER ] 请帮我读取 src/main/java/Example.java  │
 * ╰──────────────────────────────────────────────────╯
 *
 * ▶ Attempt 1...
 *
 * [ REASONING ] 思考过程...
 *
 * [ TOOL ] ReadFileTool(
 *   file_path: "src/main/java/Example.java"
 * )
 *
 * [ OUTPUT ] 文件内容...
 * </pre>
 *
 * @author coloop-agent
 */
public class ClaudeCodeStyleLoggingHook implements AgentHook {

    /** 当前循环计数 */
    private final AtomicInteger loopCount = new AtomicInteger(0);

    // ==================== 常量定义 ====================

    /** Claude Code 风格的边框角 - 左上 */
    private static final String CORNER_TOP_LEFT = "─";
    
    /** Claude Code 风格的边框角 - 右上 */
    private static final String CORNER_TOP_RIGHT = "─";
    
    /** Claude Code 风格的边框角 - 左下 */
    private static final String CORNER_BOTTOM_LEFT = "─";
    
    /** Claude Code 风格的边框角 - 右下 */
    private static final String CORNER_BOTTOM_RIGHT = "─";
    
    /** Claude Code 风格的边框 - 水平线 */
    private static final String BORDER_H = "─";
    
    /** Claude Code 风格的箭头 */
    private static final String ARROW = "▶";
    
    /** Claude Code 风格的点 */
    private static final String DOT = "•";

    /** 边框宽度 */
    private static final int BORDER_WIDTH = 54;

    // ==================== 构造方法 ====================

    /**
     * 创建一个新的 Claude Code 风格日志钩子。
     */
    public ClaudeCodeStyleLoggingHook() {
    }

    // ==================== AgentHook 实现 ====================

    @Override
    public void onLoopStart(String userMessage) {
        loopCount.set(0);
        printUserInput(userMessage);
        System.out.println();
    }

    @Override
    public void beforeLLMCall(List<Map<String, Object>> messages) {
        int count = loopCount.incrementAndGet();
        printLoopStart(count);
    }

    @Override
    public void afterLLMCall(LLMResponse response) {
        // 工具调用结果会在 onToolCall 中输出，这里可以添加额外信息
        if (!response.hasToolCalls()) {
            // 没有工具调用时，输出提示
            printThinking("");
        }
    }

    @Override
    public void onThinking(String content, String reasoningContent) {
        // 输出推理内容
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            printReasoning(reasoningContent);
        }
        // 输出思考内容
        if (content != null && !content.isEmpty()) {
            printThinking(content);
        }
    }

    @Override
    public void onToolCall(ToolCallRequest toolCall, String result, String formattedArgs) {
        printToolCall(toolCall.getName(), formattedArgs);
    }

    @Override
    public void onLoopEnd(boolean maxIter, String finalResponse) {
        System.out.println();
        if (maxIter) {
            printMaxIterationsWarning();
            if (finalResponse != null && !finalResponse.isEmpty()) {
                printOutput(finalResponse);
            }
        } else {
            printOutput(finalResponse);
        }
    }

    // ==================== 私有打印方法 ====================

    /**
     * 打印用户输入分隔符。
     *
     * <pre>
     * ═════════════════════════════════════════════════════════════
     * [ USER ] 消息内容
     * ═════════════════════════════════════════════════════════════
     * </pre>
     */
    private void printUserInput(String message) {
        String border = BORDER_H.repeat(BORDER_WIDTH);
        String label = AnsiColors.colorize("[ USER ]", AnsiColors.USER_COLOR);
        String content = truncateOrPad(message, BORDER_WIDTH);
        
        System.out.println(AnsiColors.colorize(CORNER_TOP_LEFT + border + CORNER_TOP_RIGHT, AnsiColors.SEPARATOR_COLOR));
        System.out.println(label + " " + AnsiColors.colorize(content, AnsiColors.FG_WHITE));
        System.out.println(AnsiColors.colorize(CORNER_BOTTOM_LEFT + border + CORNER_BOTTOM_RIGHT, AnsiColors.SEPARATOR_COLOR));
    }

    /**
     * 打印循环开始标记。
     *
     * <pre>
     * ▶ Attempt 1...
     * </pre>
     */
    private void printLoopStart(int count) {
        String arrow = AnsiColors.colorize(ARROW, AnsiColors.THINK_COLOR);
        String text = AnsiColors.colorize(" Attempt " + count + "...", AnsiColors.THINK_COLOR);
        System.out.println();
        System.out.println(arrow + text);
    }

    /**
     * 打印推理内容。
     *
     * <pre>
     * [ REASONING ] 推理内容...
     * </pre>
     */
    private void printReasoning(String content) {
        if (content.isEmpty()) {
            return;
        }
        content = content.trim();
        String label = AnsiColors.colorize("[ REASONING ]", AnsiColors.REASONING_COLOR);
        String text = AnsiColors.colorize(content, AnsiColors.REASONING_COLOR);
        System.out.println(label + " " + text);
    }

    /**
     * 打印思考内容。
     *
     * <pre>
     * [ THINK ] 思考内容...
     * </pre>
     */
    private void printThinking(String content) {
        if (content.isEmpty()) {
            return;
        }
        content = content.trim();
        String label = AnsiColors.colorize("[ THINK ]", AnsiColors.THINKING_CONTENT_COLOR);
        String text = AnsiColors.colorize(content, AnsiColors.THINKING_CONTENT_COLOR);
        System.out.println(label + " " + text);
    }

    /**
     * 打印工具调用。
     *
     * <pre>
     * [ TOOL ] ReadFileTool(
     *   file_path: "src/main/java/Example.java"
     * )
     * </pre>
     */
    private void printToolCall(String toolName, String formattedArgs) {
        String label = AnsiColors.colorize("[ TOOL ]", AnsiColors.TOOL_COLOR);
        String name = AnsiColors.colorize(toolName, AnsiColors.TOOL_COLOR);
        
        System.out.println(label + " " + name + AnsiColors.colorize("(", AnsiColors.TOOL_COLOR));
        
        if (formattedArgs != null && !formattedArgs.isEmpty()) {
            // 打印参数，每个参数行缩进 2 个空格
            String[] lines = formattedArgs.split("\n");
            for (String line : lines) {
                System.out.println(AnsiColors.colorize("  ", AnsiColors.TOOL_COLOR) + 
                                   AnsiColors.colorize(line, AnsiColors.FG_WHITE));
            }
        }
        
        System.out.println(AnsiColors.colorize(")", AnsiColors.TOOL_COLOR));
    }

    /**
     * 打印最终输出分隔符。
     *
     * <pre>
     * ═════════════════════════════════════════════════════════════
     * [ OUTPUT ] 最终输出内容
     * ═════════════════════════════════════════════════════════════
     * </pre>
     */
    private void printOutput(String content) {
        content = content.trim();
        String border = BORDER_H.repeat(BORDER_WIDTH);
        String label = AnsiColors.colorize("[ OUTPUT ]", AnsiColors.OUTPUT_COLOR);
        
        System.out.println(AnsiColors.colorize(CORNER_TOP_LEFT + border + CORNER_TOP_RIGHT, AnsiColors.SEPARATOR_COLOR));
        System.out.println(label + " " + AnsiColors.colorize(content, AnsiColors.FG_WHITE));
        System.out.println(AnsiColors.colorize(CORNER_BOTTOM_LEFT + border + CORNER_BOTTOM_RIGHT, AnsiColors.SEPARATOR_COLOR));
    }

    /**
     * 打印达到最大迭代警告。
     */
    private void printMaxIterationsWarning() {
        String warning = AnsiColors.colorize("⚠ Maximum iterations reached", AnsiColors.ERROR_COLOR);
        System.out.println(warning);
    }

    // ==================== 工具方法 ====================

    /**
     * 如果字符串超过最大长度，截断并添加省略号。
     */
    private String truncateOrPad(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return "...".substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 右填充字符串到指定长度。
     */
    private String padRight(String text, int length) {
        if (text == null) {
            text = "";
        }
        if (text.length() >= length) {
            return text.substring(0, length);
        }
        return text + " ".repeat(length - text.length());
    }
}
