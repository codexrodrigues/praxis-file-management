package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for validating file content using magic numbers (file signatures).
 * Magic numbers are unique byte sequences at the beginning of files that identify file types.
 */
@Component
public class MagicNumberValidator {

    private static final Logger logger = LoggerFactory.getLogger(MagicNumberValidator.class);

    private final FileManagementProperties properties;

    public MagicNumberValidator(FileManagementProperties properties) {
        this.properties = properties;
    }

    // Common magic numbers mapped to their MIME types - immutable for thread safety
    private static final Map<String, String> MAGIC_NUMBERS;

    static {
        Map<String, String> magicNumbers = new HashMap<>();
        // Images
        magicNumbers.put("FFD8FF", "image/jpeg");           // JPEG
        magicNumbers.put("89504E47", "image/png");          // PNG
        magicNumbers.put("47494638", "image/gif");          // GIF
        magicNumbers.put("424D", "image/bmp");              // BMP
        magicNumbers.put("49492A00", "image/tiff");         // TIFF (little endian)
        magicNumbers.put("4D4D002A", "image/tiff");         // TIFF (big endian)
        magicNumbers.put("52494646", "image/webp");         // WebP

        // Documents
        magicNumbers.put("25504446", "application/pdf");    // PDF
        magicNumbers.put("D0CF11E0A1B11AE1", "application/msword"); // MS Office (DOC, XLS, PPT)
        magicNumbers.put("504B0304", "application/zip");    // ZIP (also DOCX, XLSX, PPTX)
        magicNumbers.put("504B0506", "application/zip");    // ZIP (empty)
        magicNumbers.put("504B0708", "application/zip");    // ZIP (spanned)

        // Archives
        magicNumbers.put("1F8B", "application/gzip");       // GZIP
        magicNumbers.put("1F9D", "application/x-compress"); // Compress
        magicNumbers.put("1FA0", "application/x-compress"); // Compress
        magicNumbers.put("425A68", "application/x-bzip2");  // BZIP2
        magicNumbers.put("526172211A0700", "application/x-rar-compressed"); // RAR
        magicNumbers.put("377ABCAF271C", "application/x-7z-compressed");   // 7Z

        // Executables (dangerous)
        magicNumbers.put("4D5A", "application/x-msdownload"); // Windows PE
        magicNumbers.put("7F454C46", "application/x-executable"); // Linux ELF
        magicNumbers.put("CAFEBABE", "application/java-vm");   // Java class
        magicNumbers.put("FEEDFACE", "application/x-mach-binary"); // Mach-O
        magicNumbers.put("CEFAEDFE", "application/x-mach-binary"); // Mach-O

        // Audio/Video
        magicNumbers.put("494433", "audio/mpeg");           // MP3
        magicNumbers.put("FFFB", "audio/mpeg");             // MP3
        magicNumbers.put("FFF3", "audio/mpeg");             // MP3
        magicNumbers.put("FFF2", "audio/mpeg");             // MP3
        magicNumbers.put("000001BA", "video/mpeg");         // MPEG
        magicNumbers.put("000001B3", "video/mpeg");         // MPEG
        magicNumbers.put("66747970", "video/mp4");          // MP4 (starts at offset 4)

        // Text/Scripts (potentially dangerous)
        magicNumbers.put("3C3F786D6C", "application/xml");  // XML
        magicNumbers.put("3C68746D6C", "text/html");        // HTML
        magicNumbers.put("23212F", "application/x-shellscript"); // Shell script
        magicNumbers.put("2321", "application/x-shellscript");   // Shebang

        // Make the map immutable for thread safety
        MAGIC_NUMBERS = Collections.unmodifiableMap(magicNumbers);
    }

    /**
     * Validates if the file content matches expected magic numbers for the given MIME type.
     *
     * @param fileBytes The file content as byte array
     * @param expectedMimeType The MIME type detected by Tika
     * @param clientMimeType The MIME type claimed by the client
     * @return ValidationResult containing the result and details
     */
    public ValidationResult validateMagicNumbers(byte[] fileBytes, String expectedMimeType, String clientMimeType) {
        if (fileBytes == null || fileBytes.length < 4) {
            logger.warn("Security: File too small for magic number validation: {} bytes",
                fileBytes != null ? fileBytes.length : 0);
            return new ValidationResult(false, "File too small for validation", "INSUFFICIENT_DATA");
        }

        // Extract first 16 bytes for magic number analysis
        int maxBytes = Math.min(16, fileBytes.length);
        StringBuilder magicNumber = new StringBuilder();

        for (int i = 0; i < maxBytes; i++) {
            magicNumber.append(String.format("%02X", fileBytes[i] & 0xFF));
        }

        String magic = magicNumber.toString();
        // Only log first 8 chars to avoid information disclosure
        logger.debug("File magic number: {}", magic.substring(0, Math.min(8, magic.length())) + "...");

        // Check for dangerous executable patterns FIRST
        if (isDangerousExecutable(magic)) {
            logger.error("Security: Dangerous executable detected with magic: {}", magic.substring(0, Math.min(16, magic.length())));
            return new ValidationResult(false, "Executable file detected", "DANGEROUS_EXECUTABLE");
        }

        // Detect MIME type from magic numbers
        String detectedMimeFromMagic = findMimeTypeFromMagic(magic);

        // Compare client-declared MIME type with Tika-detected type
        if (clientMimeType != null && expectedMimeType != null && !clientMimeType.equals(expectedMimeType)) {
            logger.warn("Security: MIME type mismatch - Client: {}, Detected: {}", clientMimeType, expectedMimeType);
            return new ValidationResult(false,
                String.format("MIME type mismatch. Client: %s, Detected: %s", clientMimeType, expectedMimeType),
                "MIME_TYPE_MISMATCH");
        }

        // Validate magic number against detected MIME type
        if (detectedMimeFromMagic != null) {
            boolean matches = detectedMimeFromMagic.equals(expectedMimeType);

            if (!matches) {
                logger.warn("Security: Magic number mismatch - Magic indicates: {}, Tika detected: {}, Client: {}",
                    detectedMimeFromMagic, expectedMimeType, clientMimeType);
                return new ValidationResult(false,
                    String.format("File signature mismatch. Expected: %s, Found: %s",
                        expectedMimeType, detectedMimeFromMagic),
                    "SIGNATURE_MISMATCH");
            }

            logger.info("Security: Magic number validation passed for MIME type: {}", expectedMimeType);
            return new ValidationResult(true, "Magic number validation passed", "VALID");
        }

        // Check for potentially dangerous text content
        if (isPotentiallyDangerousText(fileBytes)) {
            logger.error("Security: Potentially dangerous script content detected");
            return new ValidationResult(false, "Potentially dangerous script detected", "DANGEROUS_SCRIPT");
        }

        // If no magic number found, it might be a text file or unknown format
        logger.debug("No specific magic number found, allowing based on Tika detection");
        return new ValidationResult(true, "No specific magic number validation required", "NO_MAGIC_REQUIRED");
    }

    /**
     * Finds MIME type based on magic number patterns.
     */
    private String findMimeTypeFromMagic(String magic) {
        // Check for exact matches first
        for (Map.Entry<String, String> entry : MAGIC_NUMBERS.entrySet()) {
            if (magic.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Special case for MP4 - ftyp at offset 4
        if (magic.length() >= 16 && magic.substring(8, 16).equals("66747970")) {
            return "video/mp4";
        }

        return null;
    }

    /**
     * Checks if the magic number indicates a dangerous executable file.
     */
    private boolean isDangerousExecutable(String magic) {
        String[] dangerousPatterns = {
            "4D5A",      // Windows PE executable
            "7F454C46",  // Linux ELF executable
            "CAFEBABE",  // Java class file
            "FEEDFACE",  // Mach-O executable
            "CEFAEDFE",  // Mach-O executable (64-bit)
            "23212F",    // Shell script with shebang
        };

        for (String pattern : dangerousPatterns) {
            if (magic.startsWith(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the file content contains potentially dangerous script patterns.
     */
    private boolean isPotentiallyDangerousText(byte[] fileBytes) {
        try {
            // Check configurable buffer size for dangerous patterns
            int bufferSize = properties != null ? properties.getSecurity().getScriptDetectionBufferSize() : 4096;
            int checkLength = Math.min(bufferSize, fileBytes.length);
            String content = new String(fileBytes, 0, checkLength, StandardCharsets.UTF_8).toLowerCase();

            // Check for various script patterns
            String[] dangerousPatterns = {
                "<?php",
                "<?",
                "<%",
                "<jsp:",
                "<script",
                "eval(",
                "exec(",
                "system(",
                "passthru(",
                "shell_exec("
            };

            for (String pattern : dangerousPatterns) {
                if (content.contains(pattern)) {
                    logger.warn("Security: Detected dangerous pattern in file content");
                    return true;
                }
            }
        } catch (Exception e) {
            // If we can't decode as UTF-8, it's likely binary and not a script
            logger.debug("File content is not valid UTF-8, likely binary");
        }

        return false;
    }

    /**
     * Result of magic number validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final String reasonCode;

        public ValidationResult(boolean valid, String message, String reasonCode) {
            this.valid = valid;
            this.message = Objects.requireNonNull(message, "Message cannot be null");
            this.reasonCode = Objects.requireNonNull(reasonCode, "Reason code cannot be null");
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getReasonCode() { return reasonCode; }
    }

    /**
     * Validates magic number for MultipartFile (compatibility method)
     */
    public ValidationResult validateMagicNumber(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String detectedMimeType = new org.apache.tika.Tika().detect(fileBytes);
            String clientMimeType = file.getContentType();

            return validateMagicNumbers(fileBytes, detectedMimeType, clientMimeType);
        } catch (Exception e) {
            logger.error("Error validating magic number for file: {}", file.getOriginalFilename(), e);
            return new ValidationResult(false, "Magic number validation failed: " + e.getMessage(), "VALIDATION_ERROR");
        }
    }
}
