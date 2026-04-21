package com.coloop.agent.capability.tool.exec;

import com.coloop.agent.core.tool.BaseTool;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 执行 shell 命令的工具。
 */
public class ExecTool extends BaseTool {

    private final int timeoutSeconds;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ExecTool(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }

    @Override
    public String getName() {
        return "exec";
    }

    @Override
    public String getDescription() {
        return "Execute a shell command. Returns stdout and stderr.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("type", "string");
        cmd.put("description", "Shell command to run");
        params.put("properties", Collections.singletonMap("command", cmd));
        params.put("required", Collections.singletonList("command"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object c = params.get("command");
        if (c == null) c = params.get("cmd");
        if (!(c instanceof String)) {
            return "[Error: command is required]";
        }
        String command = (String) c;

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String[] shell = isWindows
                ? new String[]{"cmd.exe", "/c", command}
                : new String[]{"/bin/sh", "-c", command};

        try {
            Process p = Runtime.getRuntime().exec(shell);
            Future<String> outFuture = executor.submit(new StreamReader(p.getInputStream()));
            Future<String> errFuture = executor.submit(new StreamReader(p.getErrorStream()));
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "[Error: command timed out after " + timeoutSeconds + "s]";
            }
            String out = outFuture.get(2, TimeUnit.SECONDS);
            String err = errFuture.get(2, TimeUnit.SECONDS);
            if (err != null && !err.isEmpty()) {
                return "stdout:\n" + (out != null ? out : "") + "\nstderr:\n" + err;
            }
            return out != null ? out : "";
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        }
    }

    private static class StreamReader implements Callable<String> {
        private final InputStream in;

        StreamReader(InputStream in) {
            this.in = in;
        }

        @Override
        public String call() throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            byte[] bytes = baos.toByteArray();
            // Try UTF-8 first, fallback to system default if replacement chars detected
            String result = new String(bytes, StandardCharsets.UTF_8);
            if (result.indexOf('�') >= 0) {
                result = new String(bytes, Charset.defaultCharset());
            }
            return result;
        }
    }
}
