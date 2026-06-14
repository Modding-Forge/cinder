package com.cinder.resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable set of integers expressed as a list of ranges. Supports negative
 * values and the OptiFine/OF-Doc list syntax {@code "1 3-7 10 12-(-5)"}.
 *
 * <p>Storage: ranges are kept as a {@code int[]} of pairs
 * {@code (start, endInclusive)} sorted by start and merged where they
 * overlap or touch. This gives O(log n) containment checks via binary
 * search and zero per-query allocation.
 *
 * <p>Behaviour matches the OF documentation:
 * <ul>
 *   <li>Single integers: {@code "5"} means the set {@code {5}}.</li>
 *   <li>Ranges: {@code "3-7"} means {@code {3, 4, 5, 6, 7}} (both ends
 *       inclusive, even when one end is negative).</li>
 *   <li>Whitespace separates tokens; ranges are written with a single
 *       dash, e.g. {@code "(-64)-0"} or {@code "5-(-3)"}.</li>
 *   <li>An empty value parses to an empty set, not a "contains everything"
 *       set. Use {@link #ALL} for the latter.</li>
 * </ul>
 *
 * <p>This class is parser-level only. It has no awareness of Minecraft
 * concepts; e.g. height ranges are just signed integer ranges.
 *
 * <p>Performance: O(n log n) on parse, O(log n) per {@link #contains(int)}
 * call thereafter. No allocation per query.
 *
 * <p>Thread expectations: instances are immutable; concurrent reads are
 * safe.
 */
public final class RangeListInt {

    /**
     * Empty set. Always returns {@code false} from {@link #contains(int)}.
     */
    public static final RangeListInt EMPTY = new RangeListInt(new int[0]);

    /**
     * Special set whose {@link #contains(int)} always returns
     * {@code true}. Used to model the absence of a filter in
     * OptiFine-style matchers. Implemented as a regular instance
     * with a single {@code [MIN_VALUE, MAX_VALUE]} range; the
     * binary-search-based {@link #contains(int)} already returns
     * {@code true} for any value in that range.
     */
    public static final RangeListInt ALL =
            new RangeListInt(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});

    private final int[] ranges;

    private RangeListInt(int[] ranges) {
        this.ranges = ranges;
    }

    /**
     * Returns {@code true} if this set represents "matches every value".
     * Equivalent to having a single range {@code [MIN_VALUE, MAX_VALUE]}.
     */
    public boolean isAll() {
        return ranges.length == 2
                && ranges[0] == Integer.MIN_VALUE
                && ranges[1] == Integer.MAX_VALUE;
    }

    /**
     * Returns {@code true} if the set has no entries.
     */
    public boolean isEmpty() {
        return ranges.length == 0;
    }

    /**
     * O(log n) containment check.
     */
    public boolean contains(int value) {
        int lo = 0;
        int hi = ranges.length / 2 - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int start = ranges[mid * 2];
            int end = ranges[mid * 2 + 1];
            if (value < start) {
                hi = mid - 1;
            } else if (value > end) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the merged, sorted ranges as a fresh array of pairs
     * {@code (start, endInclusive)}. Provided for diagnostics and for
     * serialisation in test failures.
     */
    public int[] ranges() {
        return ranges.clone();
    }

    /**
     * Parses a list like {@code "1 3-7 10"} into a {@link RangeListInt}.
     *
     * <p>Returns {@link #EMPTY} for an empty or {@code null} input.
     *
     * @throws IllegalArgumentException for malformed input
     */
    /**
     * Regex matching one of:
     * <ul>
     *   <li>{@code -?\d+} - single value,</li>
     *   <li>{@code \(-?\d+\)} - parenthesised single value,</li>
     *   <li>{@code -?\d+-(-?\d+)} - bare range,</li>
     *   <li>{@code \(-?\d+\)-(-?\d+\)} - parenthesised range.</li>
     * </ul>
     * The {@code \(...\)} forms are explicitly supported because OptiFine
     * resource packs use them to disambiguate the leading minus from a
     * range separator.
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\G\\s*(?:\\((-?\\d+)\\)|(-?\\d+))(?:\\s*-\\s*(?:\\((-?\\d+)\\)|(-?\\d+)))?\\s*");

    public static RangeListInt parse(String spec) {
        if (spec == null) {
            return EMPTY;
        }
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) {
            return EMPTY;
        }
        List<int[]> pairs = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(trimmed);
        int cursor = 0;
        while (cursor < trimmed.length() && m.find(cursor)) {
            if (m.start() != cursor) {
                throw new IllegalArgumentException(
                        "Unexpected character in range list at position "
                                + cursor + ": '" + trimmed.charAt(cursor) + "'");
            }
            String startParen = m.group(1);
            String startBare  = m.group(2);
            String endParen   = m.group(3);
            String endBare    = m.group(4);
            int start = Integer.parseInt(startParen != null ? startParen : startBare);
            int end;
            if (endParen != null || endBare != null) {
                end = Integer.parseInt(endParen != null ? endParen : endBare);
            } else {
                end = start;
            }
            if (start > end) {
                throw new IllegalArgumentException(
                        "Range has start > end in token near position " + cursor);
            }
            pairs.add(new int[]{start, end});
            cursor = m.end();
        }
        if (cursor != trimmed.length()) {
            throw new IllegalArgumentException(
                    "Trailing garbage in range list: '"
                            + trimmed.substring(cursor) + "'");
        }
        if (pairs.isEmpty()) {
            return EMPTY;
        }
        pairs.sort((a, b) -> Integer.compare(a[0], b[0]));
        int[] merged = merge(pairs);
        return new RangeListInt(merged);
    }

    private static int[] merge(List<int[]> sorted) {
        // Walk the sorted list, merging adjacent or overlapping ranges
        // into a flat int[] of (start, endInclusive) pairs.
        int[] tmp = new int[sorted.size() * 2];
        int n = 0;
        int curStart = sorted.get(0)[0];
        int curEnd = sorted.get(0)[1];
        for (int i = 1; i < sorted.size(); i++) {
            int s = sorted.get(i)[0];
            int e = sorted.get(i)[1];
            if (s <= curEnd + 1) {
                // Overlap or touching: extend the current range.
                if (e > curEnd) {
                    curEnd = e;
                }
            } else {
                tmp[n++] = curStart;
                tmp[n++] = curEnd;
                curStart = s;
                curEnd = e;
            }
        }
        tmp[n++] = curStart;
        tmp[n++] = curEnd;
        return Arrays.copyOf(tmp, n);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "RangeListInt[]";
        }
        if (isAll()) {
            return "RangeListInt[all]";
        }
        StringBuilder sb = new StringBuilder("RangeListInt[");
        for (int i = 0; i < ranges.length; i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            int s = ranges[i];
            int e = ranges[i + 1];
            if (s == e) {
                sb.append(s);
            } else {
                sb.append(s).append('-').append(e);
            }
        }
        return sb.append(']').toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RangeListInt r && Arrays.equals(ranges, r.ranges);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ranges);
    }

    /**
     * Returns an unmodifiable list of explicit values listed in the spec
     * (without expanding ranges). Useful for diagnostics and for the
     * special CTM case "tiles=0-46" where the parser cares only that the
     * range is contiguous and at least one element.
     */
    public List<Integer> enumeratedValues() {
        if (ranges.length == 0) {
            return Collections.emptyList();
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ranges.length; i += 2) {
            out.add(ranges[i]);
            out.add(ranges[i + 1]);
        }
        return out;
    }
}
