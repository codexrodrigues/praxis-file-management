package br.com.praxis.filemanagement.core.exception;

/**
 * Exception thrown when a client exceeds the configured upload rate limits.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
