package com.colin.code.core.tool;

import java.util.Map;

/**
 * 工具接口：LLM 通过名称与参数调用。
 */
public interface Tool {

    /** 工具名 */
    String getName();

    /** 工具说明，传给 LLM */
    String getDescription();

    /** JSON Schema（object），描述参数 */
    Map<String, Object> getParameters();

    /** 执行工具，返回结果字符串 */
    String execute(Map<String, Object> params);
}
