package com.coloop.agent.capability.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coloop.agent.core.provider.LLMProvider;
import com.coloop.agent.core.provider.LLMResponse;
import com.coloop.agent.core.provider.ToolCallRequest;
import com.coloop.agent.runtime.config.AppConfig;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 调用 OpenAI 兼容 API 的真实 Provider 实现。
 * 细节：HTTP 请求构造、JSON 序列化、响应解析，均已封装在此类中。
 */
public class OpenAICompatibleProvider implements LLMProvider {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String apiBase;
    private final String defaultModel;
    private final OkHttpClient client;

    public OpenAICompatibleProvider(AppConfig config) {
        this.apiKey = config.getApiKey();
        this.apiBase = config.getApiBase();
        this.defaultModel = config.getModel();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LLMResponse chat(List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools,
                            String model,
                            int maxTokens,
                            double temperature) {
        String url = apiBase.endsWith("/") ? apiBase + "chat/completions" : apiBase + "/chat/completions";

        Map<String, Object> body = new HashMap<>();
        body.put("model", model != null && !model.isEmpty() ? model : defaultModel);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        Request.Builder req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");

        try {
            String json = MAPPER.writeValueAsString(body);
            Response response = client.newCall(req.post(RequestBody.create(json, JSON)).build()).execute();
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                LLMResponse err = new LLMResponse();
                err.setContent("[LLM error: " + response.code() + " " + parseError(errBody) + "]");
                return err;
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            return parseResponse(responseBody);
        } catch (IOException e) {
            LLMResponse err = new LLMResponse();
            err.setContent("[LLM error: " + e.getMessage() + "]");
            return err;
        }
    }

    @Override
    public void chatStream(List<Map<String, Object>> messages,
                           List<Map<String, Object>> tools,
                           String model,
                           int maxTokens,
                           double temperature,
                           StreamConsumer consumer) {
        String url = apiBase.endsWith("/") ? apiBase + "chat/completions" : apiBase + "/chat/completions";

        Map<String, Object> body = new HashMap<>();
        body.put("model", model != null && !model.isEmpty() ? model : defaultModel);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        Request.Builder req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json");

        try {
            String json = MAPPER.writeValueAsString(body);
            Response response = client.newCall(req.post(RequestBody.create(json, JSON)).build()).execute();
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                consumer.onError("[LLM error: " + response.code() + " " + parseError(errBody) + "]");
                return;
            }

            StringBuilder contentBuffer = new StringBuilder();
            List<ToolCallAccumulator> toolAccs = new ArrayList<>();

            if (response.body() == null) {
                consumer.onError("[LLM error: empty response body]");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode root = MAPPER.readTree(data);
                JsonNode choices = root.get("choices");
                if (choices == null || !choices.isArray() || choices.size() == 0) {
                    continue;
                }
                JsonNode delta = choices.get(0).get("delta");
                if (delta == null) {
                    continue;
                }

                if (delta.has("content") && !delta.get("content").isNull()) {
                    String chunk = delta.get("content").asText();
                    contentBuffer.append(chunk);
                    consumer.onContent(chunk);
                }

                JsonNode tcs = delta.get("tool_calls");
                if (tcs != null && tcs.isArray()) {
                    for (JsonNode tc : tcs) {
                        int index = tc.has("index") ? tc.get("index").asInt() : 0;
                        while (toolAccs.size() <= index) {
                            toolAccs.add(new ToolCallAccumulator());
                        }
                        ToolCallAccumulator acc = toolAccs.get(index);
                        if (tc.has("id")) {
                            acc.id = tc.get("id").asText();
                        }
                        JsonNode fn = tc.get("function");
                        if (fn != null) {
                            if (fn.has("name")) {
                                acc.name = fn.get("name").asText();
                            }
                            if (fn.has("arguments")) {
                                acc.arguments.append(fn.get("arguments").asText());
                            }
                        }
                    }
                }
            }

            LLMResponse out = new LLMResponse();
            if (contentBuffer.length() > 0) {
                out.setContent(contentBuffer.toString());
            }

            if (!toolAccs.isEmpty()) {
                List<ToolCallRequest> list = new ArrayList<>();
                for (ToolCallAccumulator acc : toolAccs) {
                    ToolCallRequest tc = new ToolCallRequest();
                    tc.setId(acc.id);
                    tc.setName(acc.name);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = MAPPER.readValue(acc.arguments.toString(), Map.class);
                        tc.setArguments(args);
                    } catch (Exception ignored) {
                        tc.setArguments(Collections.<String, Object>emptyMap());
                    }
                    list.add(tc);
                    consumer.onToolCall(tc);
                }
                out.setToolCalls(list);
            }

            consumer.onComplete(out);
        } catch (Exception e) {
            consumer.onError("[LLM error: " + e.getMessage() + "]");
        }
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    // ==================== 以下为纯细节：响应解析与错误处理 ====================

    private static LLMResponse parseResponse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        LLMResponse out = new LLMResponse();
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() == 0) {
            out.setContent("");
            return out;
        }
        JsonNode msg = choices.get(0).get("message");
        if (msg != null) {
            if (msg.has("content") && !msg.get("content").isNull()) {
                out.setContent(msg.get("content").asText());
            }
            JsonNode tcs = msg.get("tool_calls");
            if (tcs != null && tcs.isArray()) {
                List<ToolCallRequest> list = new ArrayList<>();
                for (JsonNode tc : tcs) {
                    ToolCallRequest tr = new ToolCallRequest();
                    if (tc.has("id")) tr.setId(tc.get("id").asText());
                    JsonNode fn = tc.get("function");
                    if (fn != null) {
                        if (fn.has("name")) tr.setName(fn.get("name").asText());
                        if (fn.has("arguments")) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> args = MAPPER.readValue(fn.get("arguments").asText(), Map.class);
                                tr.setArguments(args);
                            } catch (Exception ignored) {
                                tr.setArguments(Collections.<String, Object>emptyMap());
                            }
                        }
                    }
                    list.add(tr);
                }
                out.setToolCalls(list);
            }
        }
        return out;
    }

    private static String parseError(String body) {
        if (body == null || body.trim().isEmpty()) return "unknown";
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode err = root.get("error");
            if (err != null && err.has("message")) return err.get("message").asText();
            if (root.has("message")) return root.get("message").asText();
        } catch (Exception ignored) {
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static class ToolCallAccumulator {
        String id = "";
        String name = "";
        StringBuilder arguments = new StringBuilder();
    }
}
