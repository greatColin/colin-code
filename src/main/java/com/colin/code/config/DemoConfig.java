package com.colin.code.config;

/**
 * 固定配置：模型参数、API 连接、执行限制等。
 * 学习用 demo 直接硬编码默认值，也可通过环境变量注入 API Key。
 */
public class DemoConfig {

    private String model = "";
    private String apiKey = "";
    private String apiBase = "";

    private int maxTokens = 2048;
    private double temperature = 0.7;
    private int maxIterations = 10;
    private int execTimeoutSeconds = 30;

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey != null ? apiKey : "";
    }

    public String getApiBase() {
        return apiBase;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getExecTimeoutSeconds() {
        return execTimeoutSeconds;
    }
}
