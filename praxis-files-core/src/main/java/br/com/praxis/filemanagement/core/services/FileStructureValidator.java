package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service for validating internal file structure beyond magic numbers.
 * Provides deep validation for ZIP and PDF files to detect malformed or malicious content.
 */
@Service
public class FileStructureValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileStructureValidator.class);

    @Autowired
    private FileManagementProperties properties;

    /**
     * Result of file structure validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String reasonCode;
        private final String message;
        private final String details;

        private ValidationResult(boolean valid, String reasonCode, String message, String details) {
            this.valid = valid;
            this.reasonCode = reasonCode;
            this.message = message;
            this.details = details;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "VALID", "File structure is valid", null);
        }

        public static ValidationResult invalid(String reasonCode, String message, String details) {
            return new ValidationResult(false, reasonCode, message, details);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String getMessage() {
            return message;
        }

        public Optional<String> getDetails() {
            return Optional.ofNullable(details);
        }
    }

    /**
     * Validate file structure based on MIME type
     */
    public ValidationResult validateFileStructure(byte[] fileBytes, String detectedMimeType, String filename) {
        if (fileBytes == null || fileBytes.length == 0) {
            return ValidationResult.invalid("EMPTY_FILE", "Cannot validate empty file structure", null);
        }

        // Add size-based timeout protection
        long validationTimeoutMs = calculateValidationTimeout(fileBytes.length);
        long startTime = System.currentTimeMillis();

        try {
            ValidationResult result;
            switch (detectedMimeType) {
                case "application/zip":
                case "application/x-zip-compressed":
                    result = validateZipStructure(fileBytes, filename);
                    break;
                
                case "application/pdf":
                    result = validatePdfStructure(fileBytes, filename);
                    break;
                
                case "application/java-archive":
                case "application/x-jar":
                    // JAR files are ZIP files, but we want to be more restrictive
                    result = validateJarStructure(fileBytes, filename);
                    break;
                
                default:
                    // No specific validation for this file type
                    logger.debug("Security: No structure validation implemented for MIME type: {}", detectedMimeType);
                    return ValidationResult.valid();
            }

            // Check if validation took too long
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > validationTimeoutMs) {
                logger.warn("Security: File structure validation timeout for file: {} - {}ms", filename, elapsedTime);
                return ValidationResult.invalid("VALIDATION_TIMEOUT", 
                    "File structure validation timeout", 
                    "Elapsed: " + elapsedTime + "ms");
            }

            return result;

        } catch (Exception e) {
            logger.error("Security: File structure validation failed for file: {} - {}", filename, e.getMessage(), e);
            return ValidationResult.invalid("VALIDATION_ERROR", 
                "File structure validation failed: " + e.getMessage(), 
                e.getClass().getSimpleName());
        }
    }

    /**
     * Calculate validation timeout based on file size
     */
    private long calculateValidationTimeout(int fileSize) {
        // Base timeout of 5 seconds + 1 second per MB
        long baseTmout = 5000;
        long sizeTimeout = (fileSize / (1024 * 1024)) * 1000;
        return Math.min(baseTmout + sizeTimeout, 30000); // Max 30 seconds
    }

    /**
     * Validate ZIP file structure
     */
    private ValidationResult validateZipStructure(byte[] fileBytes, String filename) {
        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalUncompressedSize = 0;
            boolean hasExecutableEntry = false;
            boolean hasSuspiciousEntry = false;
            String suspiciousEntryName = null;

            while ((entry = zipStream.getNextEntry()) != null) {
                entryCount++;
                
                // Check for too many entries (zip bomb protection)
                if (entryCount > 1000) { // Reduced from 10000 to 1000
                    return ValidationResult.invalid("TOO_MANY_ENTRIES", 
                        "ZIP file contains too many entries (potential zip bomb)", 
                        "Entry count: " + entryCount);
                }

                String entryName = entry.getName();
                long uncompressedSize = entry.getSize();
                
                // Check for path traversal attempts
                if (entryName.contains("../") || entryName.contains("..\\") || entryName.startsWith("/")) {
                    return ValidationResult.invalid("PATH_TRAVERSAL", 
                        "ZIP entry contains path traversal attempt", 
                        "Entry: " + entryName);
                }

                // Check for suspicious file names
                if (isSuspiciousZipEntry(entryName)) {
                    hasSuspiciousEntry = true;
                    suspiciousEntryName = entryName;
                }

                // Check for executable files
                if (isExecutableZipEntry(entryName)) {
                    hasExecutableEntry = true;
                }

                // Check for nested archives (potential zip bomb technique)
                if (isNestedArchive(entryName)) {
                    logger.warn("Security: ZIP file contains nested archive: {} in {}", entryName, filename);
                    return ValidationResult.invalid("NESTED_ARCHIVE", 
                        "ZIP file contains nested archives", 
                        "Entry: " + entryName);
                }

                // Check uncompressed size (zip bomb protection)
                if (uncompressedSize > 0) {
                    totalUncompressedSize += uncompressedSize;
                    
                    // Check for excessive uncompressed size
                    if (totalUncompressedSize > 100 * 1024 * 1024) { // Reduced to 100MB limit
                        return ValidationResult.invalid("EXCESSIVE_SIZE", 
                            "ZIP file uncompressed size too large (potential zip bomb)", 
                            "Total uncompressed: " + totalUncompressedSize + " bytes");
                    }

                    // Check compression ratio (zip bomb detection)
                    long compressedSize = entry.getCompressedSize();
                    if (compressedSize > 0) {
                        double ratio = (double) uncompressedSize / compressedSize;
                        if (ratio > 50) { // More restrictive compression ratio
                            return ValidationResult.invalid("SUSPICIOUS_COMPRESSION", 
                                "ZIP entry has suspicious compression ratio", 
                                "Entry: " + entryName + ", Ratio: " + String.format("%.2f", ratio));
                        }
                    }
                }

                zipStream.closeEntry();
            }

            // Security policy checks
            if (hasExecutableEntry) {
                logger.warn("Security: ZIP file contains executable entries: {}", filename);
                return ValidationResult.invalid("EXECUTABLE_CONTENT", 
                    "ZIP file contains executable files", null);
            }

            if (hasSuspiciousEntry) {
                logger.warn("Security: ZIP file contains suspicious entries: {} in {}", suspiciousEntryName, filename);
                return ValidationResult.invalid("SUSPICIOUS_CONTENT", 
                    "ZIP file contains suspicious files", 
                    "Entry: " + suspiciousEntryName);
            }

            if (entryCount == 0) {
                return ValidationResult.invalid("EMPTY_ARCHIVE", 
                    "ZIP file contains no entries", null);
            }

            logger.debug("Security: ZIP structure validation passed - {} entries, {} total uncompressed bytes", 
                entryCount, totalUncompressedSize);
            return ValidationResult.valid();

        } catch (IOException e) {
            logger.error("Security: Failed to validate ZIP structure for file: {}", filename, e);
            return ValidationResult.invalid("CORRUPTED_ZIP", 
                "ZIP file appears to be corrupted or malformed", 
                e.getMessage());
        }
    }

    /**
     * Validate PDF file structure
     */
    private ValidationResult validatePdfStructure(byte[] fileBytes, String filename) {
        if (fileBytes.length < 8) {
            return ValidationResult.invalid("TRUNCATED_PDF", 
                "PDF file too small to contain valid header", null);
        }

        // Prevent memory issues with very large files
        if (fileBytes.length > 50 * 1024 * 1024) { // 50MB limit for PDF content analysis
            logger.warn("Security: PDF file too large for content analysis: {} bytes", fileBytes.length);
            // Still validate basic structure but skip content analysis
            return validatePdfBasicStructure(fileBytes, filename);
        }

        // Check PDF header
        String header = new String(fileBytes, 0, Math.min(8, fileBytes.length));
        if (!header.startsWith("%PDF-")) {
            return ValidationResult.invalid("INVALID_PDF_HEADER", 
                "PDF file does not start with valid PDF header", 
                "Found: " + header);
        }

        // Extract PDF version
        if (fileBytes.length >= 8) {
            String version = new String(fileBytes, 5, 3);
            if (!isValidPdfVersion(version)) {
                return ValidationResult.invalid("INVALID_PDF_VERSION", 
                    "PDF file has invalid or unsupported version", 
                    "Version: " + version);
            }
        }

        // Use streaming approach for content analysis to avoid memory issues
        return validatePdfContent(fileBytes, filename);
    }

    /**
     * Validate basic PDF structure for large files
     */
    private ValidationResult validatePdfBasicStructure(byte[] fileBytes, String filename) {
        // Check PDF header
        String header = new String(fileBytes, 0, Math.min(8, fileBytes.length));
        if (!header.startsWith("%PDF-")) {
            return ValidationResult.invalid("INVALID_PDF_HEADER", 
                "PDF file does not start with valid PDF header", 
                "Found: " + header);
        }

        // Check for EOF marker at end of file
        int endCheckSize = Math.min(1024, fileBytes.length);
        String endContent = new String(fileBytes, fileBytes.length - endCheckSize, endCheckSize);
        if (!endContent.contains("%%EOF")) {
            logger.warn("Security: PDF file missing EOF marker: {}", filename);
            return ValidationResult.invalid("MISSING_EOF", 
                "PDF file missing end-of-file marker", null);
        }

        logger.debug("Security: PDF basic structure validation passed for large file: {}", filename);
        return ValidationResult.valid();
    }

    /**
     * Validate PDF content with memory-efficient streaming approach
     */
    private ValidationResult validatePdfContent(byte[] fileBytes, String filename) {
        // Use smaller chunks to avoid memory issues
        int chunkSize = 8192; // 8KB chunks
        boolean hasEof = false;
        
        for (int i = 0; i < fileBytes.length; i += chunkSize) {
            int endPos = Math.min(i + chunkSize, fileBytes.length);
            String chunk = new String(fileBytes, i, endPos - i);
            String lowerChunk = chunk.toLowerCase();
            
            // Check for EOF marker
            if (chunk.contains("%%EOF")) {
                hasEof = true;
            }
            
            // Check for suspicious JavaScript content
            if (lowerChunk.contains("/javascript") || 
                lowerChunk.contains("/js") ||
                lowerChunk.contains("eval(") ||
                lowerChunk.contains("unescape(") ||
                lowerChunk.contains("shell.execute") ||
                lowerChunk.contains("activexobject")) {
                return ValidationResult.invalid("SUSPICIOUS_JAVASCRIPT", 
                    "PDF file contains potentially malicious JavaScript", null);
            }

            // Check for suspicious actions
            if (lowerChunk.contains("/launch") || 
                lowerChunk.contains("/submitform") ||
                lowerChunk.contains("/importdata") ||
                lowerChunk.contains("/goto") ||
                lowerChunk.contains("/uri")) {
                return ValidationResult.invalid("SUSPICIOUS_ACTIONS", 
                    "PDF file contains potentially dangerous actions", null);
            }

            // Check for embedded files
            if (lowerChunk.contains("/embeddedfile") || lowerChunk.contains("/filespec")) {
                logger.warn("Security: PDF file contains embedded files: {}", filename);
                return ValidationResult.invalid("EMBEDDED_FILES", 
                    "PDF file contains embedded files", null);
            }
        }

        if (!hasEof) {
            logger.warn("Security: PDF file missing EOF marker: {}", filename);
            return ValidationResult.invalid("MISSING_EOF", 
                "PDF file missing end-of-file marker", null);
        }

        logger.debug("Security: PDF structure validation passed for file: {}", filename);
        return ValidationResult.valid();
    }

    /**
     * Validate JAR file structure (more restrictive than ZIP)
     */
    private ValidationResult validateJarStructure(byte[] fileBytes, String filename) {
        // First validate as ZIP
        ValidationResult zipResult = validateZipStructure(fileBytes, filename);
        if (!zipResult.isValid()) {
            return zipResult;
        }

        // Additional JAR-specific validation
        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            boolean hasManifest = false;
            boolean hasClassFiles = false;

            while ((entry = zipStream.getNextEntry()) != null) {
                String entryName = entry.getName();

                if ("META-INF/MANIFEST.MF".equals(entryName)) {
                    hasManifest = true;
                }

                if (entryName.endsWith(".class")) {
                    hasClassFiles = true;
                }

                // JAR files should not contain certain file types
                if (isSuspiciousJarEntry(entryName)) {
                    return ValidationResult.invalid("SUSPICIOUS_JAR_CONTENT", 
                        "JAR file contains suspicious content", 
                        "Entry: " + entryName);
                }

                zipStream.closeEntry();
            }

            if (!hasManifest) {
                logger.warn("Security: JAR file missing manifest: {}", filename);
                return ValidationResult.invalid("MISSING_MANIFEST", 
                    "JAR file missing required manifest", null);
            }

            logger.debug("Security: JAR structure validation passed for file: {}", filename);
            return ValidationResult.valid();

        } catch (IOException e) {
            return ValidationResult.invalid("CORRUPTED_JAR", 
                "JAR file appears to be corrupted", e.getMessage());
        }
    }

    /**
     * Check if ZIP entry name is suspicious
     */
    private boolean isSuspiciousZipEntry(String entryName) {
        String lowerName = entryName.toLowerCase();
        
        // Check for suspicious file types
        return lowerName.endsWith(".exe") || 
               lowerName.endsWith(".bat") || 
               lowerName.endsWith(".cmd") || 
               lowerName.endsWith(".com") || 
               lowerName.endsWith(".scr") || 
               lowerName.endsWith(".pif") || 
               lowerName.endsWith(".vbs") || 
               lowerName.endsWith(".js") || 
               lowerName.endsWith(".jar") ||
               lowerName.endsWith(".war") ||
               lowerName.contains("autorun");
    }

    /**
     * Check if ZIP entry is executable
     */
    private boolean isExecutableZipEntry(String entryName) {
        String lowerName = entryName.toLowerCase();
        return lowerName.endsWith(".exe") || 
               lowerName.endsWith(".msi") || 
               lowerName.endsWith(".app") || 
               lowerName.endsWith(".deb") || 
               lowerName.endsWith(".rpm");
    }

    /**
     * Check if JAR entry is suspicious
     */
    private boolean isSuspiciousJarEntry(String entryName) {
        String lowerName = entryName.toLowerCase();
        return lowerName.endsWith(".exe") || 
               lowerName.endsWith(".dll") || 
               lowerName.endsWith(".so") || 
               lowerName.endsWith(".dylib") ||
               lowerName.endsWith(".bat") ||
               lowerName.endsWith(".sh");
    }

    /**
     * Check if entry is a nested archive
     */
    private boolean isNestedArchive(String entryName) {
        String lowerName = entryName.toLowerCase();
        return lowerName.endsWith(".zip") || 
               lowerName.endsWith(".jar") || 
               lowerName.endsWith(".war") || 
               lowerName.endsWith(".ear") ||
               lowerName.endsWith(".rar") ||
               lowerName.endsWith(".7z") ||
               lowerName.endsWith(".tar") ||
               lowerName.endsWith(".gz") ||
               lowerName.endsWith(".bz2");
    }

    /**
     * Check if PDF version is valid
     */
    private boolean isValidPdfVersion(String version) {
        return version.matches("1\\.[0-7]") || version.matches("2\\.[0-9]");
    }

}