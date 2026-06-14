package com.cinder.condition;

/**
 * Parse failure for one condition property.
 *
 * <p>Purpose: later reload listeners can isolate malformed CIT or GUI rule
 * files and report the exact key/value pair without crashing the whole reload.
 *
 * <p>Threading: immutable exception object.
 *
 * <p>Performance: construction happens only during resource reload, never in
 * render or item hot paths.
 */
public final class ConditionParseException extends IllegalArgumentException {

    private final String key;
    private final String value;

    /**
     * Creates an exception for a key/value pair whose parser threw.
     */
    public ConditionParseException(String key, String value, Throwable cause) {
        super("Invalid condition property " + key + "=" + value + ": "
                + cause.getMessage(), cause);
        this.key = key;
        this.value = value;
    }

    /**
     * Creates an exception for a key/value pair with a direct message.
     */
    public ConditionParseException(String key, String value, String message) {
        super("Invalid condition property " + key + "=" + value + ": "
                + message);
        this.key = key;
        this.value = value;
    }

    /**
     * Returns the property key that failed to parse.
     */
    public String key() {
        return key;
    }

    /**
     * Returns the original property value that failed to parse.
     */
    public String value() {
        return value;
    }
}
