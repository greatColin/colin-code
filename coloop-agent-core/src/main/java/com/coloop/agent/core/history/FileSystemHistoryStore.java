package com.coloop.agent.core.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileSystemHistoryStore implements ConversationHistoryStore {

    private final Path baseDir;
    private final ObjectMapper mapper;

    public FileSystemHistoryStore(Path baseDir) {
        this.baseDir = baseDir.resolve(".history");
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            System.err.println("Failed to create history directory: " + e.getMessage());
        }
    }

    @Override
    public synchronized String createSession() {
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-"))
                + randomSuffix();
        Path sessionDir = baseDir.resolve(id);
        try {
            Files.createDirectories(sessionDir);
            SessionMeta meta = new SessionMeta(id, "New Session", System.currentTimeMillis(), System.currentTimeMillis());
            writeJson(sessionDir.resolve("meta.json"), meta);
            writeJson(sessionDir.resolve("messages.json"), new ArrayList<HistoryMessage>());
            updateIndex(meta);
        } catch (IOException e) {
            System.err.println("Failed to create session: " + e.getMessage());
        }
        return id;
    }

    @Override
    public synchronized void saveMessage(String sessionId, HistoryMessage message) {
        Path sessionDir = baseDir.resolve(sessionId);
        Path messagesFile = sessionDir.resolve("messages.json");
        try {
            List<HistoryMessage> messages;
            if (Files.exists(messagesFile)) {
                messages = mapper.readValue(messagesFile.toFile(), new TypeReference<List<HistoryMessage>>() {});
            } else {
                messages = new ArrayList<>();
            }
            messages.add(message);
            writeJson(messagesFile, messages);

            SessionMeta meta = loadSessionMeta(sessionId);
            if (meta != null) {
                meta.updatedAt = System.currentTimeMillis();
                writeJson(sessionDir.resolve("meta.json"), meta);
                updateIndex(meta);
            }
        } catch (IOException e) {
            System.err.println("Failed to save message: " + e.getMessage());
        }
    }

    @Override
    public synchronized SessionMeta loadSessionMeta(String sessionId) {
        Path file = baseDir.resolve(sessionId).resolve("meta.json");
        if (!Files.exists(file)) return null;
        try {
            return mapper.readValue(file.toFile(), SessionMeta.class);
        } catch (IOException e) {
            System.err.println("Failed to load session meta: " + e.getMessage());
            return null;
        }
    }

    @Override
    public synchronized List<HistoryMessage> loadMessages(String sessionId) {
        Path file = baseDir.resolve(sessionId).resolve("messages.json");
        if (!Files.exists(file)) return Collections.emptyList();
        try {
            return mapper.readValue(file.toFile(), new TypeReference<List<HistoryMessage>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load messages: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized List<SessionMeta> listSessions() {
        Path indexFile = baseDir.resolve("index.json");
        if (!Files.exists(indexFile)) return Collections.emptyList();
        try {
            List<SessionMeta> list = mapper.readValue(indexFile.toFile(), new TypeReference<List<SessionMeta>>() {});
            list.sort(Comparator.comparingLong((SessionMeta m) -> m.updatedAt).reversed());
            return list;
        } catch (IOException e) {
            System.err.println("Failed to list sessions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public synchronized void updateTitle(String sessionId, String title) {
        Path sessionDir = baseDir.resolve(sessionId);
        Path metaFile = sessionDir.resolve("meta.json");
        try {
            SessionMeta meta;
            if (Files.exists(metaFile)) {
                meta = mapper.readValue(metaFile.toFile(), SessionMeta.class);
            } else {
                meta = new SessionMeta();
                meta.id = sessionId;
                meta.createdAt = System.currentTimeMillis();
            }
            meta.title = title;
            meta.updatedAt = System.currentTimeMillis();
            writeJson(metaFile, meta);
            updateIndex(meta);
        } catch (IOException e) {
            System.err.println("Failed to update title: " + e.getMessage());
        }
    }

    private void updateIndex(SessionMeta meta) throws IOException {
        Path indexFile = baseDir.resolve("index.json");
        List<SessionMeta> list = new ArrayList<>();
        if (Files.exists(indexFile)) {
            list = mapper.readValue(indexFile.toFile(), new TypeReference<List<SessionMeta>>() {});
        }
        list.removeIf(m -> m.id.equals(meta.id));
        list.add(meta);
        list.sort(Comparator.comparingLong((SessionMeta m) -> m.updatedAt).reversed());
        writeJson(indexFile, list);
    }

    private void writeJson(Path path, Object value) throws IOException {
        mapper.writeValue(path.toFile(), value);
    }

    private String randomSuffix() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
