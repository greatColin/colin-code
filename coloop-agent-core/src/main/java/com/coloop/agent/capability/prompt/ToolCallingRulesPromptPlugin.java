package com.coloop.agent.capability.prompt;

import com.coloop.agent.core.prompt.PromptPlugin;
import com.coloop.agent.runtime.config.AppConfig;

import java.util.Map;

/**
 * 工具调用规则提示词插件。
 * <p>
 * 向 LLM 注入工具调用的格式约束和最佳实践，减少参数类型错误、路径格式错误等常见问题。
 *
 * @author colin.cheng.ai
 */
public class ToolCallingRulesPromptPlugin implements PromptPlugin {

    @Override
    public String getName() {
        return "tool_calling_rules";
    }

    @Override
    public int getPriority() {
        return 5; // 在 base(0) 之后，其他插件之前
    }

    @Override
    public String generate(AppConfig config, Map<String, Object> runtimeContext) {
        return """
                ## 工具调用规则

                当你生成 tool_calls 时，必须严格遵循以下规则。

                ### 参数格式
                1. **省略不需要的可选字段。** 不要用 null、""、{}、[] 作为占位符。可选参数没有值时，直接不传该字段。
                2. **容器类型必须精确。** 参数整体是 JSON object，每个字段的值类型必须匹配工具定义的 JSON Schema：
                   - string 类型：直接传字符串值，不要额外套引号或代码块。
                   - integer 类型：传数字，不要写成字符串 "30"。
                   - 数组类型：传 JSON 数组 ["a", "b"]，单元素也要括号 ["a"]。
                3. **字符串是原始字符串。** 不要包裹多余的引号、代码围栏或 markdown 格式。
                4. **数字和布尔值不加引号。** 30 而非 "30"，true 而非 "true"。

                ### 路径规则
                5. **文件路径是文件系统操作的输入。** 不要格式化为 markdown 链接、不要加反引号、不要加括号注释。
                   - 正确："/home/user/src/Main.java"
                   - 错误："[Main.java](Main.java)" 或反引号包裹
                6. **路径字段始终使用绝对路径。** read_file、write_file、edit_file、search_files、list_directory 的路径参数都需要绝对路径。

                ### 配对参数
                7. **当工具存在配对参数时（如 offset + limit、old_string + new_string），要么都传，要么都不传。** 只传一半通常会报错。

                ### 错误恢复
                8. **工具返回 [Error: ...] 格式的错误时，仔细阅读错误信息，只修复报错的部分。** 不要重写整个调用，不要用完全相同的参数重试。

                ### 工具选择
                9. **优先使用最匹配意图的专用工具。** 不要用 exec 执行 shell 命令来替代文件系统工具；read_file 读文件比 cat 更可靠，edit_file 编辑文件比 sed 更精确，search_files 搜索比 grep -r 更安全。

                ### 可用工具速查
                | 工具名 | 必需参数 | 可选参数 | 说明 |
                |--------|---------|---------|------|
                | exec | command (string) | - | 执行 shell 命令 |
                | read_file | file_path (string) | offset (int), limit (int) | 读取文件，支持按行范围 |
                | write_file | file_path (string), content (string) | - | 创建或覆盖文件 |
                | edit_file | file_path (string), old_string (string), new_string (string) | - | 精确替换文件中的文本 |
                | search_files | path (string), regex (string) | glob (string) | 正则搜索文件内容 |
                | list_directory | path (string) | - | 列出目录内容 |
                """;
    }
}
