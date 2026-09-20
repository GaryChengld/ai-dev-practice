package com.example.aipractice.exception;

/**
 * Indicates that an AI provider request failed or returned an unusable response.
 */
public class AiProviderException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message description of the provider failure
     */
    public AiProviderException(String message) {
        super(message);
    }

    /**
     * Creates an exception that wraps the underlying provider failure.
     *
     * @param message description of the provider failure
     * @param cause underlying exception
     */
    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
