package com.cinder.quad;

import com.cinder.resource.NamespaceId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link QuadDecorators} - the process-wide
 * registry and pipeline.
 */
class QuadDecoratorsTest {

    /** A test decorator that records what it sees. */
    private static final class Recorder implements QuadDecorator {
        final String id;
        final int priority;
        int calls = 0;
        QuadRef lastQuad;
        QuadContext lastContext;
        // The replacement returned for the next call (or empty).
        Optional<QuadRef> replacement = Optional.empty();

        Recorder(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        Recorder(String id) {
            this(id, 100);
        }

        @Override public String id() { return id; }
        @Override public int priority() { return priority; }
        @Override public Optional<QuadRef> decorate(QuadRef q, QuadContext c) {
            calls++;
            lastQuad = q;
            lastContext = c;
            return replacement;
        }
    }

    /** A no-op decorator that always returns empty. */
    private static final class NoopDecorator implements QuadDecorator {
        final String id;
        int calls = 0;
        NoopDecorator(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public Optional<QuadRef> decorate(QuadRef q, QuadContext c) {
            calls++;
            return Optional.empty();
        }
    }

    /** A throwing decorator. */
    private static final class ThrowingDecorator implements QuadDecorator {
        @Override public String id() { return "throwing"; }
        @Override public Optional<QuadRef> decorate(QuadRef q, QuadContext c) {
            throw new IllegalStateException("boom");
        }
    }

    /** A minimal QuadRef for tests. */
    private static final class TestQuad implements QuadRef {
        private final NamespaceId sprite;
        private final String blockId;
        TestQuad(NamespaceId sprite, String blockId) {
            this.sprite = sprite;
            this.blockId = blockId;
        }
        @Override public NamespaceId sprite() { return sprite; }
        @Override public String blockId() { return blockId; }
        @Override public int lightEmission() { return 0; }
        @Override public int tintIndex() { return -1; }
        @Override public float aoShade() { return 1.0f; }
    }

    private final NamespaceId sprite =
            new NamespaceId("minecraft", "block/glass");

    @BeforeEach
    void clearRegistry() {
        QuadDecorators.clear();
    }

    @AfterEach
    void clearRegistryAfter() {
        QuadDecorators.clear();
    }

    @Test
    void register_addsToRegistry() {
        QuadDecorators.register(new NoopDecorator("a"));
        assertEquals(1, QuadDecorators.size());
        QuadDecorators.register(new NoopDecorator("b"));
        assertEquals(2, QuadDecorators.size());
    }

    @Test
    void register_replacesById() {
        Recorder a1 = new Recorder("a");
        Recorder a2 = new Recorder("a");
        QuadDecorators.register(a1);
        QuadDecorators.register(a2);
        assertEquals(1, QuadDecorators.size());
    }

    @Test
    void register_rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> QuadDecorators.register(null));
    }

    @Test
    void pipeline_sortsByPriorityAscending() {
        Recorder low = new Recorder("low", 10);
        Recorder high = new Recorder("high", 200);
        Recorder mid = new Recorder("mid", 100);
        QuadDecorators.register(high);
        QuadDecorators.register(low);
        QuadDecorators.register(mid);
        List<QuadDecorator> pipe = QuadDecorators.pipeline();
        assertEquals(3, pipe.size());
        assertEquals("low", pipe.get(0).id());
        assertEquals("mid", pipe.get(1).id());
        assertEquals("high", pipe.get(2).id());
    }

    @Test
    void pipeline_tiesBrokenById() {
        Recorder a = new Recorder("a", 100);
        Recorder b = new Recorder("b", 100);
        Recorder c = new Recorder("c", 100);
        QuadDecorators.register(a);
        QuadDecorators.register(b);
        QuadDecorators.register(c);
        List<QuadDecorator> pipe = QuadDecorators.pipeline();
        assertEquals("a", pipe.get(0).id());
        assertEquals("b", pipe.get(1).id());
        assertEquals("c", pipe.get(2).id());
    }

    @Test
    void apply_emptyPipeline_returnsInputUnchanged() {
        QuadRef input = new TestQuad(sprite, "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1, "minecraft:glass", sprite);
        QuadRef out = QuadDecorators.apply(input, ctx);
        assertSame(input, out);
    }

    @Test
    void apply_callsEachDecoratorInOrder() {
        Recorder a = new Recorder("a", 10);
        Recorder b = new Recorder("b", 20);
        Recorder c = new Recorder("c", 30);
        QuadDecorators.register(a);
        QuadDecorators.register(b);
        QuadDecorators.register(c);
        QuadRef input = new TestQuad(sprite, "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1, "minecraft:glass", sprite);
        QuadDecorators.apply(input, ctx);
        assertEquals(1, a.calls);
        assertEquals(1, b.calls);
        assertEquals(1, c.calls);
    }

    @Test
    void apply_chainsReplacements() {
        Recorder a = new Recorder("a", 10);
        TestQuad replacement = new TestQuad(sprite, "minecraft:stone");
        a.replacement = Optional.of(replacement);
        Recorder b = new Recorder("b", 20);
        QuadDecorators.register(a);
        QuadDecorators.register(b);
        QuadRef input = new TestQuad(sprite, "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1, "minecraft:glass", sprite);
        QuadDecorators.apply(input, ctx);
        // a saw the input; b saw a's replacement.
        assertSame(input, a.lastQuad);
        assertSame(replacement, b.lastQuad);
    }

    @Test
    void apply_throwingDecorator_isBypassed() {
        Recorder a = new Recorder("a", 10);
        Recorder b = new Recorder("b", 20);
        QuadDecorators.register(new ThrowingDecorator());
        QuadDecorators.register(a);
        QuadDecorators.register(b);
        QuadRef input = new TestQuad(sprite, "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1, "minecraft:glass", sprite);
        // The pipeline must not throw. a and b must still
        // be called (the throwing decorator is bypassed,
        // the rest of the pipeline runs).
        QuadRef out = QuadDecorators.apply(input, ctx);
        assertEquals(1, a.calls);
        assertEquals(1, b.calls);
        assertSame(input, out,
                "thrown decorator must not modify the quad");
    }

    @Test
    void apply_emptyOptional_passesQuadThrough() {
        NoopDecorator noop = new NoopDecorator("noop");
        QuadDecorators.register(noop);
        QuadRef input = new TestQuad(sprite, "minecraft:glass");
        QuadContext ctx = new QuadContext(0, 0, 0, 1, "minecraft:glass", sprite);
        QuadRef out = QuadDecorators.apply(input, ctx);
        assertSame(input, out);
    }

    @Test
    void clear_removesAllDecorators() {
        QuadDecorators.register(new NoopDecorator("a"));
        QuadDecorators.register(new NoopDecorator("b"));
        QuadDecorators.clear();
        assertEquals(0, QuadDecorators.size());
    }
}
