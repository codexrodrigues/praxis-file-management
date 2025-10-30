package br.com.praxis.filemanagement.core.exception;

/**
 * Exception thrown when a tenant or user exceeds its configured quota.
 */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}
