package com.cinder.resource;

import org.junit.jupiter.api.Test;

import com.cinder.condition.ConditionCost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentMatchersTest {

    @Test
    void exactMatch() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("foo");
        assertTrue(m.matches("foo"));
        assertFalse(m.matches("bar"));
        assertFalse(m.matches("FOO"));
    }

    @Test
    void patternGlob() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("pattern:Letter to *");
        assertTrue(m.matches("Letter to a friend"));
        assertTrue(m.matches("Letter to "));
        assertFalse(m.matches("letter to a friend"));
    }

    @Test
    void ipatternGlobIsCaseInsensitive() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("ipattern:Letter to *");
        assertTrue(m.matches("Letter to a friend"));
        assertTrue(m.matches("letter to a friend"));
    }

    @Test
    void regexMatcher() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("regex:Letter (to|from) .*");
        assertTrue(m.matches("Letter to you"));
        assertTrue(m.matches("Letter from me"));
        assertFalse(m.matches("Letter via them"));
    }

    @Test
    void iregexMatcher() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("iregex:hello");
        assertTrue(m.matches("Hello"));
        assertTrue(m.matches("hello"));
    }

    @Test
    void rangeMatcher() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("range:1-5 7");
        assertTrue(m.matches(3));
        assertTrue(m.matches(5));
        assertTrue(m.matches(7));
        assertFalse(m.matches(6));
        assertFalse(m.matches(0));
    }

    @Test
    void existsTrue() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("exists:true");
        assertTrue(m.matches("anything"));
        assertTrue(m.matches(42));
    }

    @Test
    void existsFalse_neverMatches() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("exists:false");
        assertFalse(m.matches("anything", true));
        assertFalse(m.matches(42, true));
        assertTrue(m.matches((String) null, false));
    }

    @Test
    void existsTrue_requiresPresentValue() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("exists:true");
        assertTrue(m.matches("anything", true));
        assertFalse(m.matches((String) null, false));
    }

    @Test
    void negationPrefix() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("!pattern:apple*");
        assertFalse(m.matches("apple pie"));
        assertTrue(m.matches("banana"));
    }

    @Test
    void rawPrefix_isTransparent() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("raw:foo");
        assertTrue(m.matches("foo"));
    }

    @Test
    void rawPrefix_worksWithPattern() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("raw:pattern:foo*");
        assertTrue(m.matches("foobar"));
    }

    @Test
    void emptySpec_neverMatches() {
        ComponentMatchers.Compiled m = ComponentMatchers.parse("");
        assertFalse(m.matches("anything"));
        assertFalse(m.matches(0));
    }

    @Test
    void nullSpec_throws() {
        assertThrows(IllegalArgumentException.class, () -> ComponentMatchers.parse(null));
    }

    @Test
    void listParsing() {
        assertTrue(ComponentMatchers.parseList("pattern:foo* pattern:bar*").size() == 2);
        assertTrue(ComponentMatchers.parseList("").isEmpty());
    }

    @Test
    void cost_isExposedForCompiledMatchers() {
        assertEquals(ConditionCost.CHEAP,
                ComponentMatchers.parse("foo").cost());
        assertEquals(ConditionCost.CHEAP,
                ComponentMatchers.parse("range:1-3").cost());
        assertEquals(ConditionCost.EXPENSIVE,
                ComponentMatchers.parse("pattern:foo*").cost());
        assertEquals(ConditionCost.EXPENSIVE,
                ComponentMatchers.parse("regex:foo.*").cost());
    }
}
