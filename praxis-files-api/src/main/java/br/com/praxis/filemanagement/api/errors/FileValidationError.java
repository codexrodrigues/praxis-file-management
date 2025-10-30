package br.com.praxis.filemanagement.api.errors;

/**
 * Sealed class hierarchy for file validation errors (Java 17+ feature)
 * Provides type safety and exhaustive pattern matching
 */
public sealed interface FileValidationError 
    permits FileValidationError.SecurityError,
            FileValidationError.FormatError,
            FileValidationError.SystemError {

    String getMessage();
    String getErrorCode();
    Severity getSeverity();

    enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Security-related validation errors
     */
    sealed interface SecurityError extends FileValidationError
        permits SecurityError.VirusDetected,
                SecurityError.MaliciousContent,
                SecurityError.DangerousFileType {

        record VirusDetected(String virusName, String scannerVersion) implements SecurityError {
            @Override
            public String getMessage() {
                return String.format("Virus detected: %s (Scanner: %s)", virusName, scannerVersion);
            }

            @Override
            public String getErrorCode() {
                return "SEC_VIRUS_DETECTED";
            }

            @Override
            public Severity getSeverity() {
                return Severity.CRITICAL;
            }
        }

        record MaliciousContent(String contentType, String details) implements SecurityError {
            @Override
            public String getMessage() {
                return String.format("Malicious %s content detected: %s", contentType, details);
            }

            @Override
            public String getErrorCode() {
                return "SEC_MALICIOUS_CONTENT";
            }

            @Override
            public Severity getSeverity() {
                return Severity.HIGH;
            }
        }

        record DangerousFileType(String mimeType, String reason) implements SecurityError {
            @Override
            public String getMessage() {
                return String.format("Dangerous file type '%s': %s", mimeType, reason);
            }

            @Override
            public String getErrorCode() {
                return "SEC_DANGEROUS_TYPE";
            }

            @Override
            public Severity getSeverity() {
                return Severity.HIGH;
            }
        }
    }

    /**
     * Format and structure validation errors
     */
    sealed interface FormatError extends FileValidationError
        permits FormatError.InvalidMagicNumber,
                FormatError.CorruptedStructure,
                FormatError.UnsupportedFormat {

        record InvalidMagicNumber(String expected, String actual, String fileName) implements FormatError {
            @Override
            public String getMessage() {
                return String.format("Magic number mismatch in '%s': expected %s, got %s", 
                                    fileName, expected, actual);
            }

            @Override
            public String getErrorCode() {
                return "FMT_MAGIC_MISMATCH";
            }

            @Override
            public Severity getSeverity() {
                return Severity.MEDIUM;
            }
        }

        record CorruptedStructure(String fileType, String details) implements FormatError {
            @Override
            public String getMessage() {
                return String.format("Corrupted %s structure: %s", fileType, details);
            }

            @Override
            public String getErrorCode() {
                return "FMT_CORRUPTED";
            }

            @Override
            public Severity getSeverity() {
                return Severity.MEDIUM;
            }
        }

        record UnsupportedFormat(String format, String reason) implements FormatError {
            @Override
            public String getMessage() {
                return String.format("Unsupported format '%s': %s", format, reason);
            }

            @Override
            public String getErrorCode() {
                return "FMT_UNSUPPORTED";
            }

            @Override
            public Severity getSeverity() {
                return Severity.LOW;
            }
        }
    }

    /**
     * System and infrastructure errors
     */
    sealed interface SystemError extends FileValidationError
        permits SystemError.StorageError,
                SystemError.ServiceUnavailable,
                SystemError.RateLimitExceeded {

        record StorageError(String operation, String details) implements SystemError {
            @Override
            public String getMessage() {
                return String.format("Storage error during %s: %s", operation, details);
            }

            @Override
            public String getErrorCode() {
                return "SYS_STORAGE_ERROR";
            }

            @Override
            public Severity getSeverity() {
                return Severity.HIGH;
            }
        }

        record ServiceUnavailable(String serviceName, String reason) implements SystemError {
            @Override
            public String getMessage() {
                return String.format("Service '%s' unavailable: %s", serviceName, reason);
            }

            @Override
            public String getErrorCode() {
                return "SYS_SERVICE_DOWN";
            }

            @Override
            public Severity getSeverity() {
                return Severity.MEDIUM;
            }
        }

        record RateLimitExceeded(String clientId, int attemptCount, String window) implements SystemError {
            @Override
            public String getMessage() {
                return String.format("Rate limit exceeded for %s: %d attempts in %s", 
                                    clientId, attemptCount, window);
            }

            @Override
            public String getErrorCode() {
                return "SYS_RATE_LIMIT";
            }

            @Override
            public Severity getSeverity() {
                return Severity.LOW;
            }
        }
    }

    /**
     * Pattern matching helper for modern error handling (Java 17+)
     */
    static String formatErrorForLogging(FileValidationError error) {
        return switch (error) {
            case SecurityError.VirusDetected(var virus, var scanner) -> 
                String.format("[SECURITY] Virus %s detected by %s", virus, scanner);
            
            case SecurityError.MaliciousContent(var type, var details) -> 
                String.format("[SECURITY] Malicious %s: %s", type, details);
            
            case SecurityError.DangerousFileType(var mime, var reason) -> 
                String.format("[SECURITY] Dangerous type %s: %s", mime, reason);
            
            case FormatError.InvalidMagicNumber(var expected, var actual, var file) -> 
                String.format("[FORMAT] Magic mismatch in %s: %s != %s", file, actual, expected);
            
            case FormatError.CorruptedStructure(var type, var details) -> 
                String.format("[FORMAT] Corrupted %s: %s", type, details);
            
            case FormatError.UnsupportedFormat(var format, var reason) -> 
                String.format("[FORMAT] Unsupported %s: %s", format, reason);
            
            case SystemError.StorageError(var op, var details) -> 
                String.format("[SYSTEM] Storage %s failed: %s", op, details);
            
            case SystemError.ServiceUnavailable(var service, var reason) -> 
                String.format("[SYSTEM] %s unavailable: %s", service, reason);
            
            case SystemError.RateLimitExceeded(var client, var count, var window) -> 
                String.format("[SYSTEM] %s exceeded %d in %s", client, count, window);
        };
    }
}