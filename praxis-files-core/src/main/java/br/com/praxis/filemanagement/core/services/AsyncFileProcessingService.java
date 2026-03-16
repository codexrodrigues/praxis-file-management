package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Asynchronous file processing service for enterprise-grade performance
 * Handles large file uploads and CPU-intensive validation operations
 */
@Service
public class AsyncFileProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncFileProcessingService.class);

    private final FileManagementProperties properties;
    private final FileStructureValidator fileStructureValidator;
    private final MagicNumberValidator magicNumberValidator;
    private final FileIdMappingService fileIdMappingService;

    @Autowired(required = false)
    private VirusScanningService virusScanningService;

    public AsyncFileProcessingService(
            FileManagementProperties properties,
            FileStructureValidator fileStructureValidator,
            MagicNumberValidator magicNumberValidator,
            FileIdMappingService fileIdMappingService) {
        this.properties = properties;
        this.fileStructureValidator = fileStructureValidator;
        this.magicNumberValidator = magicNumberValidator;
        this.fileIdMappingService = fileIdMappingService;
    }

    /**
     * Asynchronously validate file for enterprise environments
     * Large files and virus scanning can take time, so process async
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<FileUploadResultRecord> validateFileAsync(
            MultipartFile file,
            String clientIp,
            String savedFilename) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Log processing start
                logger.debug("Starting async validation for file: {} from IP: {}",
                           file.getOriginalFilename(), clientIp);

                // Suspicious extensions blocking (if enabled)
                if (properties.getSecurity() != null && 
                    properties.getSecurity().isBlockSuspiciousExtensions()) {
                    String filename = file.getOriginalFilename();
                    if (filename != null) {
                        String extension = "";
                        int lastDotIndex = filename.lastIndexOf('.');
                        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
                            extension = filename.substring(lastDotIndex + 1).toLowerCase();
                        }
                        
                        if (properties.getSecurity().getSuspiciousExtensions().contains(extension)) {
                            logger.warn("Async validation: Suspicious extension blocked for file: {} - extension: {}", 
                                filename, extension);
                            return createFailureResult(file, FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED,
                                "Extensão de arquivo suspeita bloqueada: " + extension);
                        }
                    }
                } else {
                    logger.debug("Suspicious extension blocking is disabled for async processing of file: {}. Security check skipped.", 
                                file.getOriginalFilename());
                }

                // Magic number validation (fast) - only if enabled
                if (properties.getSecurity() != null && 
                    properties.getSecurity().isMagicNumberValidation()) {
                    // Use same API as LocalStorageFileService for consistency
                    byte[] fileBytes = file.getBytes();
                    String fileName = file.getOriginalFilename();
                    String mimeType = file.getContentType();
                    // Detect MIME type using Tika for consistency
                    String detectedMimeType = new org.apache.tika.Tika().detect(fileBytes, fileName);
                    
                    var magicValidation = magicNumberValidator.validateMagicNumbers(fileBytes, detectedMimeType, mimeType);
                    if (!magicValidation.isValid()) {
                        // Map validation result to appropriate error reason (consistent with LocalStorageFileService)
                        FileErrorReason errorReason = switch (magicValidation.getReasonCode()) {
                            case "MIME_TYPE_MISMATCH" -> FileErrorReason.MIME_TYPE_MISMATCH;
                            case "SIGNATURE_MISMATCH" -> FileErrorReason.SIGNATURE_MISMATCH;
                            case "DANGEROUS_EXECUTABLE" -> FileErrorReason.DANGEROUS_EXECUTABLE;
                            case "DANGEROUS_SCRIPT" -> FileErrorReason.DANGEROUS_SCRIPT;
                            case "INSUFFICIENT_DATA" -> FileErrorReason.INVALID_TYPE;
                            default -> FileErrorReason.UNKNOWN_ERROR;
                        };
                        return createFailureResult(file, errorReason, magicValidation.getMessage());
                    }
                } else {
                    logger.debug("Magic number validation is disabled for async processing of file: {}. Security check skipped.", 
                                file.getOriginalFilename());
                }

                // Structure validation (CPU intensive for archives)
                byte[] fileBytes = file.getBytes();
                String fileName = file.getOriginalFilename();
                String mimeType = file.getContentType();
                var structureValidation = fileStructureValidator.validateFileStructure(fileBytes, fileName, mimeType);
                if (!structureValidation.isValid()) {
                    return createFailureResult(file, FileErrorReason.DANGEROUS_FILE_TYPE,
                                             structureValidation.getMessage());
                }

                // Virus scanning (I/O intensive, network dependent, or if mandatory)
                boolean mandatoryVirusScanning = properties.getSecurity() != null && 
                                               properties.getSecurity().isMandatoryVirusScan();
                boolean regularVirusScanning = properties.getVirusScanning().isEnabled();
                
                if (mandatoryVirusScanning || regularVirusScanning) {
                    // Check if virus scanning service is available when mandatory
                    if (mandatoryVirusScanning && virusScanningService == null) {
                        logger.error("Async validation: Mandatory virus scanning is enabled but virus scanning service is not available for file: {}", fileName);
                        return createFailureResult(file, FileErrorReason.VIRUS_SCAN_UNAVAILABLE,
                                                 "Mandatory virus scanning enabled but service unavailable");
                    }
                    
                    // Perform virus scanning if service is available
                    if (virusScanningService != null) {
                        var scanResult = virusScanningService.scanFile(fileBytes, fileName);
                        if (!scanResult.isClean()) {
                            String threatInfo = scanResult.getVirusName().orElse("Unknown threat");
                            return createFailureResult(file, FileErrorReason.VIRUS_DETECTED,
                                                     "Virus or malware detected: " + threatInfo);
                        }
                        logger.debug("Async validation: File passed virus scan: {}", fileName);
                    } else if (regularVirusScanning) {
                        // Regular scanning enabled but service not available - just warn
                        logger.warn("Async validation: Virus scanning is enabled but VirusScanningService is not available for file: {}", fileName);
                    }
                }

                // All validations passed
                return createSuccessResult(file, savedFilename);

            } catch (Exception e) {
                logger.error("Async validation failed for file: {} from IP: {}",
                           file.getOriginalFilename(), clientIp, e);
                return createFailureResult(file, FileErrorReason.UNKNOWN_ERROR,
                                         "Validation processing failed: " + e.getMessage());
            }
        });
    }

    /**
     * Process multiple files concurrently
     */
    @Async("fileProcessingExecutor")
    public CompletableFuture<Void> processMultipleFilesAsync(
            MultipartFile[] files,
            String clientIp,
            java.util.function.Consumer<FileUploadResultRecord> resultCallback) {

        return CompletableFuture.runAsync(() -> {
            // Process files in parallel streams (Java 8+ feature)
            java.util.Arrays.stream(files)
                    .parallel()
                    .forEach(file -> {
                        try {
                            String savedFilename = generateFilename(file);
                            CompletableFuture<FileUploadResultRecord> result =
                                validateFileAsync(file, clientIp, savedFilename);

                            // Handle result when completed
                            result.whenComplete((uploadResult, throwable) -> {
                                if (throwable != null) {
                                    logger.error("File processing failed for: {}",
                                               file.getOriginalFilename(), throwable);
                                    uploadResult = createFailureResult(file,
                                        FileErrorReason.UNKNOWN_ERROR, throwable.getMessage());
                                }
                                resultCallback.accept(uploadResult);
                            });

                        } catch (Exception e) {
                            logger.error("Failed to initiate processing for file: {}",
                                       file.getOriginalFilename(), e);
                            resultCallback.accept(createFailureResult(file,
                                FileErrorReason.UNKNOWN_ERROR, e.getMessage()));
                        }
                    });
        });
    }

    /**
     * Create success result with modern Java features
     */
    private FileUploadResultRecord createSuccessResult(MultipartFile file, String savedFilename) {
        String originalFilename = normalizeOriginalFilename(file);
        String fileId = fileIdMappingService.generateFileId(
            savedFilename,
            originalFilename,
            "/upload", // Default upload path for async processing
            file.getSize(),
            file.getContentType()
        );
        
        return FileUploadResultRecord.success(
            originalFilename,
            savedFilename,
            fileId,
            file.getSize(),
            file.getContentType()
        );
    }

    /**
     * Create failure result with error details
     */
    private FileUploadResultRecord createFailureResult(MultipartFile file,
                                                FileErrorReason reason,
                                                String message) {
        return FileUploadResultRecord.error(
            normalizeOriginalFilename(file),
            reason,
            message
        );
    }

    private String normalizeOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank()
            ? "unknown"
            : originalFilename;
    }

    /**
     * Generate unique filename using UUID (Java 17+ approach)
     */
    private String generateFilename(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()) {
            return "unknown_" + java.util.UUID.randomUUID();
        }

        // Use text block for better readability (Java 15+)
        String template = """
            %s_%s_%s
            """.strip();

        String baseName = originalName.substring(0,
            originalName.lastIndexOf('.') >= 0 ? originalName.lastIndexOf('.') : originalName.length());
        String extension = originalName.lastIndexOf('.') >= 0 ?
            originalName.substring(originalName.lastIndexOf('.')) : "";
        String timestamp = String.valueOf(System.currentTimeMillis());

        return String.format(template, baseName, timestamp, java.util.UUID.randomUUID()).trim() + extension;
    }
}
