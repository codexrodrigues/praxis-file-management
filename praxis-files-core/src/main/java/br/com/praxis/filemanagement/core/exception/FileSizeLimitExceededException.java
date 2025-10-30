package br.com.praxis.filemanagement.core.exception;

/**
 * Exception thrown when an uploaded file exceeds the configured maximum size.
 */
public class FileSizeLimitExceededException extends RuntimeException {
    private final long fileSize;
    private final long maxAllowedSize;

    public FileSizeLimitExceededException(long fileSize, long maxAllowedSize) {
        super("File size " + fileSize + " exceeds maximum allowed " + maxAllowedSize);
        this.fileSize = fileSize;
        this.maxAllowedSize = maxAllowedSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getMaxAllowedSize() {
        return maxAllowedSize;
    }
}
