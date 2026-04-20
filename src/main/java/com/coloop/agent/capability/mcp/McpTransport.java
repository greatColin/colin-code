package com.coloop.agent.capability.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.util.Map;

public class McpTransport {
    private final McpServerConfig config;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean connected = false;

    public McpTransport(McpServerConfig config) {
        this.config = config;
    }

    public synchronized void connect() throws McpException {
        if (connected) return;

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(config.getCommand(), config.getArgs().toArray(new String[0]));

            if (config.getEnv() != null) {
                Map<String, String> env = pb.environment();
                env.putAll(config.getEnv());
            }

            pb.redirectErrorStream(true);
            process = pb.start();

            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            connected = true;
        } catch (IOException e) {
            throw new McpException("Failed to start MCP server: " + e.getMessage(), e);
        }
    }

    public synchronized void sendRequest(ObjectNode request) throws McpException {
        if (!connected) {
            throw new McpException("Not connected to MCP server");
        }
        try {
            String json = objectMapper.writeValueAsString(request) + "\n";
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            throw new McpException("Failed to send request: " + e.getMessage(), e);
        }
    }

    public synchronized ObjectNode readResponse() throws McpException {
        if (!connected) {
            throw new McpException("Not connected to MCP server");
        }
        try {
            String line = reader.readLine();
            if (line == null) {
                connected = false;
                throw new McpException("Server process terminated unexpectedly");
            }
            return objectMapper.readValue(line, ObjectNode.class);
        } catch (IOException e) {
            connected = false;
            throw new McpException("Failed to read response: " + e.getMessage(), e);
        }
    }

    public synchronized void close() {
        connected = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (process != null) process.destroyForcibly();
        } catch (IOException e) {
            // ignore
        }
    }

    public boolean isConnected() { return connected; }
}