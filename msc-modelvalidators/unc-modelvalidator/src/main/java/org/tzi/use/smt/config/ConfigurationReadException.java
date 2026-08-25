package org.tzi.use.smt.config;

/** Raised for unreadable, malformed, or structurally invalid configuration input. */
public final class ConfigurationReadException extends IllegalArgumentException {
    public ConfigurationReadException(String message) {
        super(message);
    }

    public ConfigurationReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
