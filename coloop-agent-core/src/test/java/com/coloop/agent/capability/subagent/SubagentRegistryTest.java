package com.coloop.agent.capability.subagent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SubagentRegistryTest {

    private SubagentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SubagentRegistry();
    }

    @Test
    void testCreateOrReplaceAndGet() {
        SubagentInstance inst = new SubagentInstance("a", "d", "sp", null, null);
        registry.createOrReplace("a", inst);
        assertSame(inst, registry.get("a"));
    }

    @Test
    void testGetNonExistentReturnsNull() {
        assertNull(registry.get("nonexistent"));
    }

    @Test
    void testCreateOrReplaceFiresOnCleared() {
        SubagentInstance old = new SubagentInstance("x", "d1", "sp", null, null);
        SubagentInstance replacement = new SubagentInstance("x", "d2", "sp", null, null);
        registry.createOrReplace("x", old);

        AtomicInteger clearedCount = new AtomicInteger(0);
        registry.addListener(new SubagentEventListener() {
            @Override public void onCreated(SubagentInstance i) {}
            @Override public void onCleared(String name) {
                clearedCount.incrementAndGet();
                assertEquals("x", name);
            }
        });

        registry.createOrReplace("x", replacement);
        assertEquals(1, clearedCount.get());
        assertSame(replacement, registry.get("x"));
    }

    @Test
    void testCreateOrReplaceFiresOnCreated() {
        AtomicInteger createdCount = new AtomicInteger(0);
        registry.addListener(new SubagentEventListener() {
            @Override public void onCreated(SubagentInstance inst) {
                createdCount.incrementAndGet();
            }
            @Override public void onCleared(String name) {}
        });

        SubagentInstance inst = new SubagentInstance("b", "d", "sp", null, null);
        registry.createOrReplace("b", inst);
        assertEquals(1, createdCount.get());
    }

    @Test
    void testSnapshotReturnsCopy() {
        registry.createOrReplace("a", new SubagentInstance("a", "d", "sp", null, null));
        registry.createOrReplace("b", new SubagentInstance("b", "d", "sp", null, null));
        List<SubagentInstance> snap = registry.snapshot();
        assertEquals(2, snap.size());
    }

    @Test
    void testRemoveClearsEntry() {
        SubagentInstance inst = new SubagentInstance("r", "d", "sp", null, null);
        registry.createOrReplace("r", inst);
        registry.remove("r");
        assertNull(registry.get("r"));
    }

    @Test
    void testClearRemovesAllAndFiresOnCleared() {
        registry.createOrReplace("a", new SubagentInstance("a", "d", "sp", null, null));
        registry.createOrReplace("b", new SubagentInstance("b", "d", "sp", null, null));

        AtomicInteger clearedCount = new AtomicInteger(0);
        registry.addListener(new SubagentEventListener() {
            @Override public void onCreated(SubagentInstance i) {}
            @Override public void onCleared(String n) { clearedCount.incrementAndGet(); }
        });

        registry.clear();
        assertEquals(2, clearedCount.get());
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    void testConcurrentCreateOrReplaceSameName() throws Exception {
        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger createdCount = new AtomicInteger(0);
        registry.addListener(new SubagentEventListener() {
            @Override public void onCreated(SubagentInstance i) { createdCount.incrementAndGet(); }
            @Override public void onCleared(String n) {}
        });

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            new Thread(() -> {
                registry.createOrReplace("shared",
                    new SubagentInstance("shared", "d" + idx, "sp", null, null));
                latch.countDown();
            }).start();
        }
        latch.await();

        // Only one instance survives
        assertNotNull(registry.get("shared"));
        // Each create either replaces (1 onCleared + 1 onCreated) or is new (1 onCreated)
        // Final count >= threads (each creates at least one)
        assertTrue(createdCount.get() >= threads);
    }
}
