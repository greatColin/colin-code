package com.coloop.agent.capability.hook;

/**
 * ANSI 颜色代码工具类。
 * 用于在控制台输出中添加颜色和样式。
 *
 * <p>使用方式：
 * <pre>
 * System.out.println(AnsiColors.GREEN + "绿色文字" + AnsiColors.RESET);
 * System.out.println(AnsiColors.colorize("绿色文字", AnsiColors.FG_GREEN));
 * </pre>
 * </p>
 *
 * <p>颜色代码参考：
 * <ul>
 *   <li>前景色: \u001b[30m - \u001b[37m (黑、红、绿、黄、蓝、紫、青、白)</li>
 *   <li>背景色: \u001b[40m - \u001b[47m</li>
 *   <li>样式: \u001b[1m (粗体), \u001b[2m (暗色), \u001b[3m (斜体), \u001b[4m (下划线)</li>
 *   <li>重置: \u001b[0m</li>
 * </ul>
 * </p>
 */
public final class AnsiColors {

    private AnsiColors() {
        // 工具类，不允许实例化
    }

    // ==================== 前景色 (文本颜色) ====================
    
    /** 黑色 */
    public static final String FG_BLACK = "\u001b[30m";
    
    /** 红色 */
    public static final String FG_RED = "\u001b[31m";
    
    /** 绿色 */
    public static final String FG_GREEN = "\u001b[32m";
    
    /** 黄色 */
    public static final String FG_YELLOW = "\u001b[33m";
    
    /** 蓝色 */
    public static final String FG_BLUE = "\u001b[34m";
    
    /** 紫色/品红 */
    public static final String FG_MAGENTA = "\u001b[35m";
    
    /** 青色 */
    public static final String FG_CYAN = "\u001b[36m";
    
    /** 白色 */
    public static final String FG_WHITE = "\u001b[37m";
    
    /** 亮红色 */
    public static final String FG_BRIGHT_RED = "\u001b[91m";
    
    /** 亮绿色 */
    public static final String FG_BRIGHT_GREEN = "\u001b[92m";
    
    /** 亮黄色 */
    public static final String FG_BRIGHT_YELLOW = "\u001b[93m";
    
    /** 亮蓝色 */
    public static final String FG_BRIGHT_BLUE = "\u001b[94m";
    
    /** 亮紫色 */
    public static final String FG_BRIGHT_MAGENTA = "\u001b[95m";
    
    /** 亮青色 */
    public static final String FG_BRIGHT_CYAN = "\u001b[96m";
    
    /** 亮白色 */
    public static final String FG_BRIGHT_WHITE = "\u001b[97m";

    // ==================== 背景色 ====================
    
    /** 黑色背景 */
    public static final String BG_BLACK = "\u001b[40m";
    
    /** 红色背景 */
    public static final String BG_RED = "\u001b[41m";
    
    /** 绿色背景 */
    public static final String BG_GREEN = "\u001b[42m";
    
    /** 黄色背景 */
    public static final String BG_YELLOW = "\u001b[43m";
    
    /** 蓝色背景 */
    public static final String BG_BLUE = "\u001b[44m";
    
    /** 紫色背景 */
    public static final String BG_MAGENTA = "\u001b[45m";
    
    /** 青色背景 */
    public static final String BG_CYAN = "\u001b[46m";
    
    /** 白色背景 */
    public static final String BG_WHITE = "\u001b[47m";

    // ==================== 样式 ====================
    
    /** 重置所有样式 */
    public static final String RESET = "\u001b[0m";
    
    /** 粗体/明亮 */
    public static final String BOLD = "\u001b[1m";
    
    /** 暗色 */
    public static final String DIM = "\u001b[2m";
    
    /** 斜体 */
    public static final String ITALIC = "\u001b[3m";
    
    /** 下划线 */
    public static final String UNDERLINE = "\u001b[4m";

    // ==================== Claude Code 风格颜色 ====================
    
    /** Claude Code 用户输入颜色 - 亮绿色 */
    public static final String USER_COLOR = FG_BRIGHT_GREEN;
    
    /** Claude Code 思考/循环开始颜色 - 亮蓝色 */
    public static final String THINK_COLOR = FG_BRIGHT_BLUE;
    
    /** Claude Code 推理内容颜色 - 紫色 */
    public static final String REASONING_COLOR = FG_BRIGHT_MAGENTA;
    
    /** Claude Code 思考内容颜色 - 亮蓝色 */
    public static final String THINKING_CONTENT_COLOR = FG_BRIGHT_CYAN;
    
    /** Claude Code 工具调用颜色 - 亮黄色/橙色 */
    public static final String TOOL_COLOR = FG_BRIGHT_YELLOW;
    
    /** Claude Code 最终输出颜色 - 默认白色 */
    public static final String OUTPUT_COLOR = FG_WHITE;
    
    /** Claude Code 错误/警告颜色 - 亮红色 */
    public static final String ERROR_COLOR = FG_BRIGHT_RED;

    /** Task progress color - cyan */
    public static final String TASK_COLOR = FG_BRIGHT_CYAN;

    /** Claude Code 分隔线颜色 - 暗灰色 */
    public static final String SEPARATOR_COLOR = FG_WHITE;

    // ==================== 便捷方法 ====================

    /**
     * 为文本添加颜色。
     *
     * @param text  文本
     * @param color 颜色代码（如 FG_GREEN）
     * @return 带颜色前缀和重置后缀的文本
     */
    public static String colorize(String text, String color) {
        return color + text + RESET;
    }

    /**
     * 为文本添加粗体样式。
     *
     * @param text 文本
     * @return 带粗体前缀和重置后缀的文本
     */
    public static String bold(String text) {
        return BOLD + text + RESET;
    }

    /**
     * 为文本添加下划线样式。
     *
     * @param text 文本
     * @return 带下划线前缀和重置后缀的文本
     */
    public static String underline(String text) {
        return UNDERLINE + text + RESET;
    }

    /**
     * 创建一个带颜色和样式的标签。
     *
     * @param label      标签文本
     * @param labelColor 标签颜色
     * @param text       内容文本
     * @param textColor  内容颜色（可选，null 表示不设置颜色）
     * @return 格式化后的文本
     */
    public static String label(String label, String labelColor, String text, String textColor) {
        if (textColor != null) {
            return colorize("[" + label + "]", labelColor) + " " + colorize(text, textColor);
        } else {
            return colorize("[" + label + "]", labelColor) + " " + text;
        }
    }

    /**
     * 创建 Claude Code 风格的带标签的输出。
     *
     * @param label 标签
     * @param text  内容
     * @return 格式化后的文本
     */
    public static String claudeLabel(String label, String text) {
        return colorize("[ " + label + " ]", THINK_COLOR) + " " + text;
    }
}
