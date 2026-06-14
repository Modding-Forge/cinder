package com.cinder.resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal loader for {@code .properties} files as used by OptiFine-compatible
 * resource packs.
 *
 * <p>This is <b>not</b> {@link java.util.Properties}. The OptiFine format
 * differs from {@code java.util.Properties} in a handful of ways that matter
 * for resource-pack behavior:
 *
 * <ul>
 *   <li>Key/value separator is {@code =} only. {@code :} is not a separator
 *       (it is meaningful in block-spec syntax such as
 *       {@code minecraft:oak_stairs:facing=east}).</li>
 *   <li>Values are <b>not</b> subject to backslash escape processing. Backslashes
 *       are part of the value verbatim; only line terminators separate values.</li>
 *   <li>Keys are case-sensitive. {@code MatchBlocks} and {@code matchBlocks}
 *       are two different keys (the OF loader treats them as such).</li>
 *   <li>Comment lines start with {@code #} only. {@code !} is not a comment
 *       prefix.</li>
 *   <li>Lines are trimmed only of leading and trailing ASCII whitespace.
 *       Internal whitespace is preserved.</li>
 *   <li>Empty keys and empty values are allowed (some OptiFine keys are
 *       used as flags where the presence of the key is the signal).</li>
 *   <li>Duplicate keys keep the first occurrence. The OptiFine convention is
 *       last-wins for the {@code .properties} files themselves, but the
 *       common-resource-pack tooling in the wild relies on first-wins when
 *       merging; first-wins is also simpler and easier to test.</li>
 * </ul>
 *
 * <p>This implementation is a clean-room load routine; it shares no code
 * with {@code java.util.Properties} beyond a few immutable map utilities.
 *
 * <p>Performance: O(n) in the file size, single pass. No allocation per
 * line beyond the {@code LinkedHashMap} entry.
 *
 * <p>Thread expectations: instances are immutable after construction;
 * concurrent reads are safe.
 */
public final class PropertiesFile {

    /**
     * Parsed key/value store. Iteration order matches the file order for
     * debugging; the {@link #get(String)} contract is case-sensitive.
     */
    private final Map<String, String> entries;

    private PropertiesFile(Map<String, String> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    /**
     * Reads a {@code .properties} file from the given path using UTF-8.
     *
     * @throws IOException if the file cannot be read
     */
    public static PropertiesFile read(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    /**
     * Parses a {@code .properties} stream from the given reader. The reader
     * is fully consumed but not closed by this method.
     */
    public static PropertiesFile parse(Reader source) throws IOException {
        BufferedReader reader;
        if (source instanceof BufferedReader br) {
            reader = br;
        } else {
            reader = new BufferedReader(source);
        }

        Map<String, String> map = new LinkedHashMap<>();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = stripAsciiWhitespace(line);
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                // The OptiFine convention is that a key without an '=' is a
                // flag whose presence is the signal; the value is "".
                if (!map.containsKey(trimmed)) {
                    map.put(trimmed, "");
                }
                continue;
            }
            String key = stripAsciiWhitespace(trimmed.substring(0, eq));
            String value = stripAsciiWhitespace(trimmed.substring(eq + 1));
            if (key.isEmpty()) {
                throw new IOException("Empty key on line " + lineNumber);
            }
            // First-wins: ignore subsequent occurrences of the same key.
            map.putIfAbsent(key, value);
        }
        return new PropertiesFile(map);
    }

    /**
     * Returns the value for {@code key}, or {@code null} if the key is not
     * present. Keys are case-sensitive.
     */
    public String get(String key) {
        return entries.get(key);
    }

    /**
     * Returns the value for {@code key}, or {@code defaultValue} if the key
     * is not present.
     */
    public String get(String key, String defaultValue) {
        String v = entries.get(key);
        return v != null ? v : defaultValue;
    }

    /**
     * Returns the value for {@code key} split on ASCII whitespace into a
     * {@code String[]}. Useful for keys whose value is a list of tokens
     * (e.g. {@code matchBlocks=oak_planks spruce_planks}).
     */
    public String[] getTokens(String key) {
        String v = entries.get(key);
        if (v == null) {
            return new String[0];
        }
        return v.trim().split("\\s+");
    }

    /**
     * Returns an unmodifiable view of all entries in file order. Useful for
     * iterating "every key" without exposing mutability.
     */
    public Map<String, String> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    private static String stripAsciiWhitespace(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isAsciiWhitespace(s.charAt(start))) {
            start++;
        }
        while (end > start && isAsciiWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isAsciiWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }
}
