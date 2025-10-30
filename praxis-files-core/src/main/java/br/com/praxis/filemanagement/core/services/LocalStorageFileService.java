package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.BulkUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.utils.FileServiceMessageAdapter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import br.com.praxis.filemanagement.core.validation.InputValidationService;
import br.com.praxis.filemanagement.core.validation.InputValidationService.ValidationResult;
import br.com.praxis.filemanagement.core.exception.FileSizeLimitExceededException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Map;
import java.time.Instant;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.config.FileManagementProperties.BulkUpload;
import br.com.praxis.filemanagement.core.audit.SecurityAuditLogger;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.utils.FileNameHandler;
import br.com.praxis.filemanagement.core.services.ThreadSafeFileNamingService;
import br.com.praxis.filemanagement.core.services.ThreadSafeFileNamingService.UniqueNameResult;
import br.com.praxis.filemanagement.core.services.FileIdMappingService;
import br.com.praxis.filemanagement.core.services.AtomicFileOperationService;
import br.com.praxis.filemanagement.core.services.AtomicFileOperationService.UploadTransaction;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class LocalStorageFileService implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageFileService.class);

    private final Tika tika = new Tika();

    @Autowired
    private MagicNumberValidator magicNumberValidator;

    @Autowired
    private FileManagementProperties fileManagementProperties;

    @Autowired(required = false)
    private FileUploadMetricsService metricsService;

    @Autowired
    private ThreadSafeFileNamingService fileNamingService;

    @Autowired
    private FileIdMappingService fileIdMappingService;

    @Autowired
    private AtomicFileOperationService atomicFileOperationService;

    @Autowired
    private InputValidationService inputValidationService;

    // Helper methods for optional metrics
    private void recordFailedUpload(Timer.Sample sample, FileErrorReason reason) {
        if (metricsService != null) {
            metricsService.recordFailedUpload(sample, reason);
        }
    }

    private void recordSecurityRejection(String reason) {
        if (metricsService != null) {
            metricsService.recordSecurityRejection(reason);
        }
    }

    private void recordSuccessfulUpload(Timer.Sample sample) {
        if (metricsService != null) {
            metricsService.recordSuccessfulUpload(sample);
        }
    }

    @Autowired(required = false)
    private VirusScanningService virusScanningService;

    @Autowired
    private FileStructureValidator fileStructureValidator;

    @Autowired
    private SecurityAuditLogger securityAuditLogger;

    @Autowired
    private UploadAttemptTracker uploadAttemptTracker;

    @Autowired(required = false)
    private HttpServletRequest request;

    /**
     * Realiza upload seguro de um arquivo único aplicando todas as validações de segurança configuradas.
     *
     * <p>Este método é o ponto de entrada principal para upload de arquivos individuais, aplicando:
     * <ul>
     *   <li><strong>Validação de Entrada</strong>: Validação robusta usando InputValidationService</li>
     *   <li><strong>Detecção MIME</strong>: Análise de conteúdo usando Apache Tika</li>
     *   <li><strong>Magic Numbers</strong>: Validação de assinatura binária para detectar tipos reais</li>
     *   <li><strong>Escaneamento de Vírus</strong>: Detecção opcional via ClamAV</li>
     *   <li><strong>Validação Estrutural</strong>: Análise de integridade para ZIP, PDF, JAR</li>
     *   <li><strong>Política de Conflitos</strong>: Gerenciamento de nomes duplicados conforme configurado</li>
     *   <li><strong>Operações Atômicas</strong>: Upload com rollback automático em caso de falha</li>
     * </ul>
     *
     * <p><strong>Processo de Validação:</strong>
     * <ol>
     *   <li>Validação de entrada (arquivo vazio, tamanho, etc.)</li>
     *   <li>Detecção segura de MIME type via conteúdo</li>
     *   <li>Validação de magic numbers contra tipos perigosos</li>
     *   <li>Escaneamento opcional de vírus (se habilitado)</li>
     *   <li>Validação estrutural do arquivo</li>
     *   <li>Aplicação de política de conflito de nomes</li>
     *   <li>Operação atômica de armazenamento</li>
     * </ol>
     *
     * <p><strong>Auditoria e Logging:</strong>
     * Todas as operações são registradas para auditoria de segurança, incluindo:
     * tentativas de upload, violações de segurança, detecções de malware e uploads bem-sucedidos.
     *
     * @param file Arquivo multipart a ser enviado (não pode ser null)
     * @param options Opções de configuração de upload (pode ser null para usar padrões).
     *                Quando null, utiliza configurações padrão do sistema incluindo:
     *                <ul>
     *                  <li>Política de conflito: MAKE_UNIQUE</li>
     *                  <li>Validação rigorosa conforme configuração global</li>
     *                  <li>Tipos MIME permitidos conforme configuração global</li>
     *                </ul>
     * @return FileUploadResultRecord contendo:
     *         <ul>
     *           <li><strong>Sucesso</strong>: fileId, originalFilename, serverFilename, fileSize, mimeType, uploadTimestamp</li>
     *           <li><strong>Falha</strong>: originalFilename, errorReason, errorMessage, uploadTimestamp</li>
     *         </ul>
     * @throws SecurityException Se detectada tentativa de path traversal (nunca retornado via result)
     * @since 1.0.0
     * @see FileUploadOptionsRecord
     * @see FileUploadResultRecord
     * @see NameConflictPolicy
     */
    @Override
    public FileUploadResultRecord uploadFile(MultipartFile file, FileUploadOptionsRecord options) {
        String clientIp = getClientIp();
        FileUploadResultRecord result = uploadFileInternal(file, options, clientIp);
        if (!result.success() && result.errorReason() == FileErrorReason.FILE_TOO_LARGE) {
            long effectiveMaxBytes = resolveEffectiveMaxSizeBytes(options);
            throw new FileSizeLimitExceededException(result.fileSize(), effectiveMaxBytes);
        }
        return result;
    }

    /**
     * Internal upload method that accepts client IP as parameter for use in async contexts
     */
    private FileUploadResultRecord uploadFileInternal(MultipartFile file, FileUploadOptionsRecord options, String clientIp) {
        // Start metrics timer
        Timer.Sample timerSample = metricsService != null ? metricsService.startUploadTimer() : null;

        return performUploadWithRecord(file, options, timerSample, clientIp);
    }


    private FileUploadResultRecord performUploadWithRecord(MultipartFile file, FileUploadOptionsRecord options, Timer.Sample timerSample, String clientIp) {

        // Validação robusta de entrada antes de qualquer processamento usando Records
        ValidationResult validation = inputValidationService.validateUploadFile(file, options);
        if (!validation.isValid()) {
            logger.warn("Input validation failed for file: {} - {}",
                       file.getOriginalFilename(), validation.getErrorSummary());
            recordFailedUpload(timerSample, validation.getPrimaryErrorReason());

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), validation.getPrimaryErrorReason());

            // Log security violation for validation failure
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), null, validation.getPrimaryErrorReason(),
                    validation.getErrorSummary(), "Input validation failed: " + validation.getErrorSummary());
            }

            return FileUploadResultRecord.error(
                file.getOriginalFilename(),
                validation.getPrimaryErrorReason(),
                validation.getErrorSummary()
            );
        }

        // Track upload attempt
        uploadAttemptTracker.recordUploadAttempt(clientIp, file.getOriginalFilename(), file.getSize());

        // Log upload started
        if (fileManagementProperties.getAuditLogging().isEnabled()) {
            securityAuditLogger.logUploadStarted(clientIp, file.getOriginalFilename(),
                file.getSize(), file.getContentType());
        }

        if (file.isEmpty()) {
            logger.warn("Upload attempt with empty file: {}", file.getOriginalFilename());
            recordFailedUpload(timerSample, FileErrorReason.EMPTY_FILE);

            // Log security violation
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), null, FileErrorReason.EMPTY_FILE,
                    "Empty file not allowed", "Empty file upload attempt");
            }

            return FileServiceMessageAdapter.createEmptyFileRecord(
                file.getOriginalFilename(),
                file.getSize()
            );
        }

        // Validate file size before processing
        long maxSizeBytes;
        long maxSizeMb;
        Long optMb = options != null ? options.maxUploadSizeMb() : null;
        if (optMb != null && optMb > 0) {
            // Check for potential overflow in MB to bytes conversion
            if (optMb > Long.MAX_VALUE / (1024 * 1024)) {
                logger.error("Maximum file size configuration causes overflow: {} MB", optMb);
                recordFailedUpload(timerSample, FileErrorReason.UNKNOWN_ERROR);

                // Track failed upload
                uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.UNKNOWN_ERROR);

                // Log security violation for configuration error
                if (fileManagementProperties.getAuditLogging().isEnabled()) {
                    securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                        file.getSize(), null, FileErrorReason.UNKNOWN_ERROR,
                        "Configuration error: max size overflow", "Configuration error: max size overflow");
                }

                return FileServiceMessageAdapter.createConfigurationErrorRecord(
                    file.getOriginalFilename(),
                    file.getSize()
                );
            }
            maxSizeMb = optMb;
            maxSizeBytes = maxSizeMb * 1024 * 1024;
        } else {
            maxSizeBytes = fileManagementProperties.getMaxFileSizeBytes();
            maxSizeMb = maxSizeBytes / (1024 * 1024);
        }

        if (maxSizeBytes > 0 && file.getSize() > maxSizeBytes) {
            logger.warn("File too large: {} bytes, max allowed: {} MB", file.getSize(), maxSizeMb);
            recordFailedUpload(timerSample, FileErrorReason.FILE_TOO_LARGE);

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.FILE_TOO_LARGE);

            // Log security violation
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), null, FileErrorReason.FILE_TOO_LARGE,
                    "File size exceeds limit", "File size exceeds limit: " + file.getSize() + " bytes");
            }

            return FileServiceMessageAdapter.createFileSizeExceededRecord(
                file.getSize(),
                maxSizeMb,
                file.getOriginalFilename()
            );
        }

        // Use new FileNameHandler for consistent filename handling
        String sanitizedFilename = FileNameHandler.sanitize(file.getOriginalFilename());
        FileNameHandler.FileNameComponents nameComponents = FileNameHandler.decompose(file.getOriginalFilename());
        String fileExtension = nameComponents.extension();

        // Secure MIME Type detection using file content
        String detectedMimeType;
        byte[] fileBytes;
        try {
            // Use getBytes() to avoid stream consumption issues
            fileBytes = file.getBytes();
            detectedMimeType = tika.detect(fileBytes);
            logger.info("Detected MIME type: {} for file: {}", detectedMimeType, sanitizedFilename);
        } catch (IOException e) {
            logger.error("Failed to detect MIME type for file: {}", sanitizedFilename, e);
            recordFailedUpload(timerSample, FileErrorReason.UNKNOWN_ERROR);

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.UNKNOWN_ERROR);

            // Log security violation for MIME detection failure
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), null, FileErrorReason.UNKNOWN_ERROR,
                    "MIME type detection failure: " + e.getMessage(), "MIME type detection failure: " + e.getMessage());
            }

            return FileServiceMessageAdapter.createFileAnalysisErrorRecord(
                file.getOriginalFilename(),
                file.getSize()
            );
        }

        // Check global MIME type restrictions
        if (!isGloballyAllowedMimeType(detectedMimeType)) {
            logger.warn("Security: Globally blocked MIME type rejected: {}", detectedMimeType);
            recordFailedUpload(timerSample, FileErrorReason.INVALID_TYPE);
            recordSecurityRejection("blocked_mime_type");

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.INVALID_TYPE);

            // Log security violation
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), detectedMimeType, FileErrorReason.INVALID_TYPE,
                    "Globally blocked MIME type: " + detectedMimeType, "Globally blocked MIME type: " + detectedMimeType);
            }

            return FileServiceMessageAdapter.createFileTypeNotAllowedRecord(
                file.getOriginalFilename(),
                file.getSize()
            );
        }

        // Suspicious extensions blocking (if enabled)
        if (fileManagementProperties.getSecurity() != null &&
            fileManagementProperties.getSecurity().isBlockSuspiciousExtensions()) {
            String filename = file.getOriginalFilename();
            if (filename != null) {
                String extension = "";
                int lastDotIndex = filename.lastIndexOf('.');
                if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
                    extension = filename.substring(lastDotIndex + 1).toLowerCase();
                }

                if (fileManagementProperties.getSecurity().getSuspiciousExtensions().contains(extension)) {
                    logger.warn("Security: Suspicious extension blocked for file: {} - extension: {}",
                        sanitizedFilename, extension);
                    recordFailedUpload(timerSample, FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED);
                    recordSecurityRejection("suspicious_extension_blocked");

                    // Track failed upload
                    uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED);

                    // Log security violation
                    if (fileManagementProperties.getAuditLogging().isEnabled()) {
                        securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                            file.getSize(), detectedMimeType, FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED,
                            "Suspicious file extension blocked: " + extension, "Suspicious extension: " + extension);
                    }

                    return FileUploadResultRecord.error(
                        file.getOriginalFilename(),
                        FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED,
                        "Extensão de arquivo suspeita bloqueada: " + extension
                    );
                }
            }
        } else {
            // Log debug when suspicious extension blocking is disabled
            logger.debug("Suspicious extension blocking is disabled for file: {}. Security check skipped.", sanitizedFilename);
        }

        // Magic number validation for enhanced security (if enabled)
        if (fileManagementProperties.getSecurity() != null &&
            fileManagementProperties.getSecurity().isMagicNumberValidation()) {
            MagicNumberValidator.ValidationResult magicValidation =
                magicNumberValidator.validateMagicNumbers(fileBytes, detectedMimeType, file.getContentType());

            if (!magicValidation.isValid()) {
                // Map validation result to appropriate error reason
                FileErrorReason errorReason = switch (magicValidation.getReasonCode()) {
                    case "MIME_TYPE_MISMATCH" -> FileErrorReason.MIME_TYPE_MISMATCH;
                    case "SIGNATURE_MISMATCH" -> FileErrorReason.SIGNATURE_MISMATCH;
                    case "DANGEROUS_EXECUTABLE" -> FileErrorReason.DANGEROUS_EXECUTABLE;
                    case "DANGEROUS_SCRIPT" -> FileErrorReason.DANGEROUS_SCRIPT;
                    case "INSUFFICIENT_DATA" -> FileErrorReason.INVALID_TYPE;
                    default -> FileErrorReason.UNKNOWN_ERROR;
                };

                logger.warn("Security: Magic number validation failed for file: {} - {}",
                    sanitizedFilename, magicValidation.getMessage());
                recordFailedUpload(timerSample, errorReason);
                recordSecurityRejection("magic_number_validation");

                // Track failed upload
                uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), errorReason);

                // Log security violation
                if (fileManagementProperties.getAuditLogging().isEnabled()) {
                    securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                        file.getSize(), detectedMimeType, errorReason,
                        magicValidation.getMessage(), "Magic number validation failed: " + magicValidation.getReasonCode());
                }

                return FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    errorReason,
                    magicValidation.getMessage()
                );
            }
        } else {
            // Log debug when magic number validation is disabled
            logger.debug("Magic number validation is disabled for file: {}. Security check skipped.", sanitizedFilename);
        }

        // Virus scanning (if enabled and available, or if mandatory)
        boolean mandatoryVirusScanning = fileManagementProperties.getSecurity() != null &&
                                       fileManagementProperties.getSecurity().isMandatoryVirusScan();
        boolean regularVirusScanning = fileManagementProperties.getVirusScanning().isEnabled();

        if (mandatoryVirusScanning || regularVirusScanning) {
            // Check if virus scanning service is available when mandatory
            if (mandatoryVirusScanning && virusScanningService == null) {
                logger.error("Security: Mandatory virus scanning is enabled but virus scanning service is not available for file: {}", sanitizedFilename);
                recordFailedUpload(timerSample, FileErrorReason.VIRUS_SCAN_UNAVAILABLE);
                recordSecurityRejection("mandatory_virus_scan_unavailable");

                // Track failed upload
                uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.VIRUS_SCAN_UNAVAILABLE);

                // Log security violation for mandatory scan unavailable
                if (fileManagementProperties.getAuditLogging().isEnabled()) {
                    securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                        file.getSize(), detectedMimeType, FileErrorReason.VIRUS_SCAN_UNAVAILABLE,
                        "Mandatory virus scanning enabled but service unavailable", "Mandatory virus scanning service unavailable");
                }

                return FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.VIRUS_SCAN_UNAVAILABLE,
                    "Scanner de vírus obrigatório não está disponível"
                );
            }

            // Perform virus scanning if service is available
            if (virusScanningService != null) {
            VirusScanningService.ScanResult scanResult = virusScanningService.scanFile(fileBytes, sanitizedFilename);

            if (scanResult.isError()) {
                logger.error("Security: Virus scan failed for file: {} - {}",
                    sanitizedFilename, scanResult.getErrorMessage().orElse("Unknown error"));
                recordFailedUpload(timerSample, FileErrorReason.UNKNOWN_ERROR);
                recordSecurityRejection("virus_scan_error");

                // Track failed upload
                uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.UNKNOWN_ERROR);

                // Log security event for virus scan failure
                if (fileManagementProperties.getAuditLogging().isEnabled()) {
                    securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                        file.getSize(), detectedMimeType, FileErrorReason.UNKNOWN_ERROR,
                        "Virus scan failure: " + scanResult.getErrorMessage().orElse("Unknown error"), "Virus scan failure: " + scanResult.getErrorMessage().orElse("Unknown error"));
                }

                return FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.UNKNOWN_ERROR,
                    "Falha ao escanear arquivo: " + scanResult.getErrorMessage().orElse("Erro desconhecido")
                );
            }

            if (!scanResult.isClean()) {
                logger.warn("Security: Malware detected in file: {} - Virus: {}",
                    sanitizedFilename, scanResult.getVirusName().orElse("Unknown virus"));
                recordFailedUpload(timerSample, FileErrorReason.MALWARE_DETECTED);
                recordSecurityRejection("malware_detected");

                // Track failed upload
                uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.MALWARE_DETECTED);

                // Log malware detection
                if (fileManagementProperties.getAuditLogging().isEnabled()) {
                    securityAuditLogger.logMalwareDetected(clientIp, file.getOriginalFilename(),
                        file.getSize(), scanResult.getVirusName().orElse("Unknown virus"), "ClamAV");
                }

                return FileServiceMessageAdapter.createMalwareDetectedRecord(
                    scanResult.getVirusName().orElse("Unknown virus"),
                    file.getOriginalFilename(),
                    file.getSize()
                );
            }

                logger.debug("Security: File passed virus scan: {}", sanitizedFilename);
            }
        }

        // File structure validation for supported file types
        FileStructureValidator.ValidationResult structureValidation =
            fileStructureValidator.validateFileStructure(fileBytes, detectedMimeType, sanitizedFilename);

        if (!structureValidation.isValid()) {
            // Map structure validation result to appropriate error reason
            FileErrorReason errorReason = switch (structureValidation.getReasonCode()) {
                case "TOO_MANY_ENTRIES", "EXCESSIVE_SIZE", "SUSPICIOUS_COMPRESSION", "NESTED_ARCHIVE" -> FileErrorReason.ZIP_BOMB_DETECTED;
                case "PATH_TRAVERSAL" -> FileErrorReason.PATH_TRAVERSAL;
                case "EXECUTABLE_CONTENT", "SUSPICIOUS_CONTENT", "SUSPICIOUS_JAR_CONTENT" -> FileErrorReason.EMBEDDED_EXECUTABLE;
                case "EMBEDDED_FILES", "SUSPICIOUS_JAVASCRIPT", "SUSPICIOUS_ACTIONS" -> FileErrorReason.SUSPICIOUS_STRUCTURE;
                case "CORRUPTED_ZIP", "CORRUPTED_JAR", "TRUNCATED_PDF", "INVALID_PDF_HEADER", "MISSING_EOF" -> FileErrorReason.CORRUPTED_FILE;
                case "INVALID_PDF_VERSION", "MISSING_MANIFEST", "EMPTY_ARCHIVE" -> FileErrorReason.SUSPICIOUS_STRUCTURE;
                default -> FileErrorReason.UNKNOWN_ERROR;
            };

            logger.warn("Security: File structure validation failed for file: {} - {} ({})",
                sanitizedFilename, structureValidation.getMessage(), structureValidation.getReasonCode());

            // Add details if available
            if (structureValidation.getDetails().isPresent()) {
                logger.warn("Security: Structure validation details: {}", structureValidation.getDetails().get());
            }

            recordFailedUpload(timerSample, errorReason);
            recordSecurityRejection("structure_validation");

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), errorReason);

            // Log security violation
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                String securityDetails = "File structure validation failed: " + structureValidation.getReasonCode();
                if (structureValidation.getDetails().isPresent()) {
                    securityDetails += " - " + structureValidation.getDetails().get();
                }
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), detectedMimeType, errorReason,
                    structureValidation.getMessage(), securityDetails);
            }

            return FileUploadResultRecord.error(
                file.getOriginalFilename(),
                errorReason,
                structureValidation.getMessage()
            );
        }

        // Validate file type and perform cross-validation
        if (!isValidFileType(options, fileExtension, detectedMimeType, file.getContentType())) {
            logger.warn("Security: Invalid file type rejected - Extension: {}, Detected MIME: {}, Client MIME: {}",
                fileExtension, detectedMimeType, file.getContentType());
            recordFailedUpload(timerSample, FileErrorReason.INVALID_TYPE);
            recordSecurityRejection("invalid_file_type");

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.INVALID_TYPE);

            // Log security violation
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                String securityDetails = String.format("File type validation failed - Extension: %s, Detected MIME: %s, Client MIME: %s",
                    fileExtension, detectedMimeType, file.getContentType());
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), detectedMimeType, FileErrorReason.INVALID_TYPE,
                    "File type not allowed", securityDetails);
            }

            return FileUploadResultRecord.error(
                file.getOriginalFilename(),
                FileErrorReason.INVALID_TYPE,
                String.format("File type not allowed - Extension: %s, Detected MIME: %s",
                            fileExtension, detectedMimeType)
            );
        }


        NameConflictPolicy conflictPolicy = (options != null && options.nameConflictPolicy() != null)
                ? options.nameConflictPolicy()
                : fileManagementProperties.getDefaultConflictPolicy();

        Path uploadPath = Paths.get(fileManagementProperties.getUploadDir()).toAbsolutePath().normalize();
        Path tempPath = Paths.get(fileManagementProperties.getUploadTempDir()).toAbsolutePath().normalize();
        Path destinationPath;
        String serverFilename;

        try {
            Files.createDirectories(uploadPath); // Ensure directory exists
            Files.createDirectories(tempPath);

            // Handle Name Conflict Policy
            switch (conflictPolicy) {
                case ERROR:
                    serverFilename = sanitizedFilename; // Use original sanitized name
                    destinationPath = uploadPath.resolve(serverFilename).normalize();
                    if (Files.exists(destinationPath)) {
                        recordFailedUpload(timerSample, FileErrorReason.FILE_EXISTS);

                        // Track failed upload
                        uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.FILE_EXISTS);

                        // Log upload failure
                        if (fileManagementProperties.getAuditLogging().isEnabled()) {
                            securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                                file.getSize(), detectedMimeType, FileErrorReason.FILE_EXISTS,
                                "File already exists - conflict policy: ERROR", "File already exists - conflict policy: ERROR");
                        }

                        return FileServiceMessageAdapter.createFileExistsRecord(
                            file.getOriginalFilename(),
                            file.getSize()
                        );
                    }
                    break;
                case SKIP:
                    serverFilename = sanitizedFilename; // Use original sanitized name
                    destinationPath = uploadPath.resolve(serverFilename).normalize();
                    if (Files.exists(destinationPath)) {
                        recordFailedUpload(timerSample, FileErrorReason.FILE_EXISTS);

                        // Track failed upload
                        uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.FILE_EXISTS);

                        // Log upload failure
                        if (fileManagementProperties.getAuditLogging().isEnabled()) {
                            securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                                file.getSize(), detectedMimeType, FileErrorReason.FILE_EXISTS,
                                "File already exists - conflict policy: SKIP", "File already exists - conflict policy: SKIP");
                        }

                        return FileServiceMessageAdapter.createFileExistsRecord(
                            file.getOriginalFilename(),
                            file.getSize()
                        );
                    }
                    break;
                case OVERWRITE:
                    serverFilename = sanitizedFilename; // Use original sanitized name
                    destinationPath = uploadPath.resolve(serverFilename).normalize();
                    // No check needed, file.transferTo will overwrite if exists
                    break;
                case MAKE_UNIQUE:
                    serverFilename = UUID.randomUUID() + (fileExtension.isEmpty() ? "" : "." + fileExtension);
                    destinationPath = uploadPath.resolve(serverFilename).normalize();
                    break;
                case RENAME:
                    try {
                        // Use thread-safe naming service for RENAME policy
                        UniqueNameResult namingResult = fileNamingService.generateUniqueNameForRename(
                            sanitizedFilename, uploadPath);
                        serverFilename = namingResult.finalName();
                        destinationPath = uploadPath.resolve(serverFilename).normalize();

                        // Log naming strategy used for debugging/monitoring
                        logger.debug("RENAME policy used strategy: {} after {} attempts for file: {}",
                                   namingResult.strategy(), namingResult.attemptsUsed(), file.getOriginalFilename());
                    } catch (IOException e) {
                        logger.error("Error generating unique name for RENAME policy: {}", e.getMessage(), e);
                        // Fallback to MAKE_UNIQUE behavior
                        serverFilename = UUID.randomUUID() + (fileExtension.isEmpty() ? "" : "." + fileExtension);
                        destinationPath = uploadPath.resolve(serverFilename).normalize();
                    }
                    break;
                default: // Default behavior if policy is null or unknown
                    serverFilename = UUID.randomUUID() + (fileExtension.isEmpty() ? "" : "." + fileExtension);
                    destinationPath = uploadPath.resolve(serverFilename).normalize();
                    break;
            }

            // Security check: ensure file stays within upload directory
            // destinationPath is already normalized above, so no need to normalize again
            if (!destinationPath.startsWith(uploadPath)) {
                logger.error("Security: Path traversal attempt blocked: {}", destinationPath);
                recordFailedUpload(timerSample, FileErrorReason.INVALID_PATH);
                recordSecurityRejection("path_traversal");

                // Track failed upload
                uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.INVALID_PATH);

                // Log security violation
                if (fileManagementProperties.getAuditLogging().isEnabled()) {
                    securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                        file.getSize(), detectedMimeType, FileErrorReason.INVALID_PATH,
                        "Path traversal attempt: " + destinationPath, "Path traversal attempt: " + destinationPath);
                }

                return FileServiceMessageAdapter.createPathTraversalRecord(
                    file.getOriginalFilename(),
                    file.getSize()
                );
            }

            // Execute atomic upload operation with automatic rollback on failure using Records
            UploadTransaction transaction = atomicFileOperationService.executeAtomicUpload(
                file,
                destinationPath,
                serverFilename,
                detectedMimeType,
                uploadPath,
                tempPath
            );

            logger.debug("Atomic upload completed successfully. Transaction: {}, Steps: {}",
                       transaction.getTransactionId(), transaction.getStepCount());

            logger.info("File uploaded successfully: {} -> {}", file.getOriginalFilename(), serverFilename);
            recordSuccessfulUpload(timerSample);

            // Track successful upload
            uploadAttemptTracker.recordSuccessfulUpload(clientIp, file.getOriginalFilename(), file.getSize());

            // Log successful upload
            if (fileManagementProperties.getAuditLogging().isEnabled() &&
                fileManagementProperties.getAuditLogging().isLogSuccessfulUploads()) {
                securityAuditLogger.logUploadSuccess(clientIp, file.getOriginalFilename(),
                    serverFilename, file.getSize(), detectedMimeType, null);
            }

            // Generate unique file ID for the uploaded file
            String fileId = fileIdMappingService.generateFileId(
                serverFilename,
                file.getOriginalFilename(),
                uploadPath.toString(),
                file.getSize(),
                detectedMimeType
            );

            return FileUploadResultRecord.success(
                file.getOriginalFilename(),
                serverFilename,
                fileId,
                file.getSize(),
                detectedMimeType
            );

        } catch (IOException e) {
            logger.error("Failed to store file: {}", file.getOriginalFilename(), e);
            recordFailedUpload(timerSample, FileErrorReason.UNKNOWN_ERROR);

            // Track failed upload
            uploadAttemptTracker.recordFailedUpload(clientIp, file.getOriginalFilename(), FileErrorReason.UNKNOWN_ERROR);

            // Log security violation for file system error
            if (fileManagementProperties.getAuditLogging().isEnabled()) {
                securityAuditLogger.logSecurityViolation(clientIp, file.getOriginalFilename(),
                    file.getSize(), detectedMimeType, FileErrorReason.UNKNOWN_ERROR,
                    "File system error: " + e.getClass().getSimpleName(), "File system error: " + e.getClass().getSimpleName());
            }

            return FileServiceMessageAdapter.createFileStoreErrorRecord(
                file.getOriginalFilename(),
                file.getSize()
            );
        }
    }


    private boolean isValidFileType(FileUploadOptionsRecord options, String fileExtension, String detectedMimeType, String clientMimeType) {
        if (options == null) {
            return true; // No options provided, no validation
        }

        boolean extensionAllowed = true;
        if (options.allowedExtensions() != null && !options.allowedExtensions().isEmpty()) {
            extensionAllowed = options.allowedExtensions().contains(fileExtension.toLowerCase());
        }

        boolean mimeTypeAllowed = true;
        if (options.acceptMimeTypes() != null && !options.acceptMimeTypes().isEmpty()) {
            mimeTypeAllowed = options.acceptMimeTypes().contains(detectedMimeType);
        }

        // Cross-validation: check if detected MIME type matches expected type for extension
        if (detectedMimeType != null && clientMimeType != null && !detectedMimeType.equals(clientMimeType)) {
            logger.warn("Security: MIME type mismatch detected - Client: {}, Detected: {}", clientMimeType, detectedMimeType);
            // For strict validation, log the mismatch but continue with type validation
        }

        return options.strictValidation() ? extensionAllowed && mimeTypeAllowed : extensionAllowed || mimeTypeAllowed;
    }



    /**
     * Check if MIME type is globally allowed based on configuration
     */
    private boolean isGloballyAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        FileManagementProperties.Security security = fileManagementProperties.getSecurity();

        // Check if it's a dangerous MIME type
        if (security.getDangerousMimeTypes().contains(mimeType)) {
            logger.warn("Security: Dangerous MIME type detected: {}", mimeType);
            return false;
        }

        // Check if HTML files are blocked
        if (security.isBlockHtmlFiles() && "text/html".equals(mimeType)) {
            logger.warn("Security: HTML files are blocked by policy");
            return false;
        }

        // Check blocked MIME types
        if (security.getBlockedMimeTypes().contains(mimeType)) {
            logger.warn("Security: Blocked MIME type: {}", mimeType);
            return false;
        }

        // Check allowed MIME types (if configured)
        if (!security.getAllowedMimeTypes().isEmpty()) {
            boolean allowed = security.getAllowedMimeTypes().contains(mimeType);
            if (!allowed) {
                logger.warn("Security: MIME type not in allowed list: {}", mimeType);
            }
            return allowed;
        }

        return true;
    }

    /**
     * Get client IP address from request
     */
    private String getClientIp() {
        if (request == null) {
            return "unknown";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Log bulk upload operation for audit trail
     */
    private void logBulkUploadAudit(BulkUploadResultRecord bulkResult, String clientIp) {
        if (fileManagementProperties.getAuditLogging() != null &&
            fileManagementProperties.getAuditLogging().isEnabled()) {

            try {
                // Log consolidated bulk upload event (using HashMap to avoid Map.of() parameter limit)
                Map<String, Object> auditData = new java.util.HashMap<>();
                auditData.put("event_type", "BULK_UPLOAD");
                auditData.put("client_ip", clientIp);
                auditData.put("total_files", bulkResult.totalProcessed());
                auditData.put("successful_files", bulkResult.totalSuccess());
                auditData.put("failed_files", bulkResult.totalFailed());
                auditData.put("overall_success", bulkResult.overallSuccess());
                auditData.put("processing_time_ms", bulkResult.processingTimeMs());
                auditData.put("total_size_bytes", bulkResult.totalSizeBytes());
                auditData.put("success_rate_percent", bulkResult.getSuccessRate());
                auditData.put("start_timestamp", bulkResult.startTimestamp().toString());
                auditData.put("end_timestamp", bulkResult.endTimestamp().toString());

                logger.info("AUDIT_BULK_UPLOAD: {}", auditData);

                // Log failed files for security monitoring
                if (bulkResult.totalFailed() > 0) {
                    bulkResult.getFailedResults().forEach(failedResult -> {
                        Map<String, Object> failureAudit = new java.util.HashMap<>();
                        failureAudit.put("event_type", "BULK_UPLOAD_FAILURE");
                        failureAudit.put("client_ip", clientIp);
                        failureAudit.put("filename", failedResult.originalFilename());
                        failureAudit.put("error_reason", failedResult.errorReason().toString());
                        failureAudit.put("error_message", failedResult.errorMessage() != null ? failedResult.errorMessage() : "N/A");
                        failureAudit.put("file_size", failedResult.fileSize());
                        failureAudit.put("timestamp", failedResult.uploadTimestamp().toString());

                        logger.warn("AUDIT_BULK_UPLOAD_FAILURE: {}", failureAudit);
                    });
                }

                // Security alert for high failure rate
                double failureRate = 100.0 - bulkResult.getSuccessRate();
                if (failureRate > 50.0 && bulkResult.totalProcessed() > 5) {
                    Map<String, Object> securityAlert = new java.util.HashMap<>();
                    securityAlert.put("event_type", "BULK_UPLOAD_HIGH_FAILURE_RATE");
                    securityAlert.put("client_ip", clientIp);
                    securityAlert.put("failure_rate_percent", failureRate);
                    securityAlert.put("total_files", bulkResult.totalProcessed());
                    securityAlert.put("failed_files", bulkResult.totalFailed());
                    securityAlert.put("timestamp", bulkResult.endTimestamp().toString());

                    logger.error("SECURITY_ALERT_BULK_UPLOAD: {}", securityAlert);
                }

            } catch (Exception e) {
                logger.error("Failed to log bulk upload audit information", e);
            }
        }
    }

    // ========================================================================
    // BULK UPLOAD METHODS - NEW FUNCTIONALITY
    // ========================================================================

    /**
     * Realiza upload múltiplo de arquivos com processamento paralelo ou sequencial conforme configuração.
     *
     * <p>Este método oferece duas estratégias de processamento:
     * <ul>
     *   <li><strong>Modo Paralelo (padrão)</strong>: Processa até 3 arquivos simultaneamente para máxima performance</li>
     *   <li><strong>Modo Fail-Fast</strong>: Processamento sequencial que para no primeiro erro</li>
     * </ul>
     *
     * <p><strong>Validações Pré-Processamento:</strong>
     * <ul>
     *   <li>Limite de arquivos por lote (padrão: 50 arquivos)</li>
     *   <li>Limite de tamanho total do payload (padrão: 100MB)</li>
     *   <li>Verificação de array vazio ou null</li>
     * </ul>
     *
     * <p><strong>Processamento Paralelo (failFastMode = false):</strong>
     * <ul>
     *   <li>Utiliza thread pool limitado para controlar concorrência</li>
     *   <li>Cada arquivo é processado independentemente</li>
     *   <li>Falhas individuais não interrompem outros uploads</li>
     *   <li>Timeout configurável para evitar travamentos (padrão: 300s)</li>
     *   <li>Shutdown gracioso do executor com cleanup automático</li>
     * </ul>
     *
     * <p><strong>Modo Fail-Fast (failFastMode = true):</strong>
     * <ul>
     *   <li>Processamento sequencial (um arquivo por vez)</li>
     *   <li>Para imediatamente no primeiro erro encontrado</li>
     *   <li>Marca arquivos restantes como BULK_UPLOAD_CANCELLED</li>
     *   <li>Ideal para validação rápida e lotes pequenos</li>
     * </ul>
     *
     * <p><strong>Auditoria e Monitoramento:</strong>
     * <ul>
     *   <li>Log consolidado de estatísticas do lote</li>
     *   <li>Log individual de falhas para monitoramento de segurança</li>
     *   <li>Alertas automáticos para alta taxa de falhas (>50% em lotes >5 arquivos)</li>
     *   <li>Métricas de performance e throughput</li>
     * </ul>
     *
     * <p><strong>Gestão de Recursos:</strong>
     * O método garante cleanup adequado de threads e recursos mesmo em caso de exceções,
     * usando try-finally com shutdown gracioso do ExecutorService.
     *
     * @param files Array de arquivos multipart para upload (não pode ser null ou vazio).
     *              Limitado por configuração (padrão: máximo 50 arquivos por lote)
     * @param options Opções de configuração aplicadas a todos os arquivos do lote (pode ser null).
     *                Propriedades importantes:
     *                <ul>
     *                  <li><strong>failFastMode</strong>: true = sequencial com parada no erro, false = paralelo</li>
     *                  <li><strong>strictValidation</strong>: Aplica validação rigorosa em todos os arquivos</li>
     *                  <li><strong>nameConflictPolicy</strong>: Política aplicada a todos os arquivos</li>
     *                  <li><strong>maxUploadSizeMb</strong>: Limite por arquivo individual</li>
     *                </ul>
     * @return BulkUploadResultRecord contendo:
     *         <ul>
     *           <li><strong>Estatísticas</strong>: totalProcessed, totalSuccess, totalFailed, totalCancelled</li>
     *           <li><strong>Performance</strong>: processingTimeMs, successRate, overallSuccess</li>
     *           <li><strong>Resultados</strong>: Lista completa de FileUploadResultRecord para cada arquivo</li>
     *           <li><strong>Metadados</strong>: Informações de processamento e timestamps</li>
     *           <li><strong>Indicadores</strong>: wasFailFastTriggered para identificar interrupções</li>
     *         </ul>
     * @throws IllegalArgumentException Se files for null (encapsulado no resultado)
     * @since 1.0.0
     * @see BulkUploadResultRecord
     * @see FileUploadOptionsRecord
     * @see FileManagementProperties.BulkUpload
     */
    @Override
    public BulkUploadResultRecord uploadMultipleFiles(MultipartFile[] files, FileUploadOptionsRecord options) {
        if (files == null || files.length == 0) {
            logger.info("No files provided for bulk upload");
            return BulkUploadResultRecord.fromResults(List.of());
        }

        logger.info("Starting bulk upload of {} files", files.length);
        Instant startTime = Instant.now();

        // Check if fail-fast mode is enabled
        if (options != null && options.failFastMode()) {
            logger.info("Fail-fast mode enabled - processing files sequentially");
            return forceFailFastMode(files, options, startTime);
        }

        // Validate number of files limit
        BulkUpload bulkConfig = fileManagementProperties.getBulkUpload();
        int maxFiles = bulkConfig != null ? bulkConfig.getMaxFilesPerBatch() : 50;

        if (files.length > maxFiles) {
            logger.warn("Bulk upload file count {} exceeds limit {}", files.length, maxFiles);

            List<FileUploadResultRecord> failedResults = new ArrayList<>();
            for (MultipartFile file : files) {
                failedResults.add(FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.RATE_LIMIT_EXCEEDED,
                    "Bulk upload file count exceeds limit of " + maxFiles + " files"
                ));
            }

            return BulkUploadResultRecord.fromResults(failedResults, startTime, Instant.now());
        }

        // Validate total payload size
        long totalSize = 0;
        for (MultipartFile file : files) {
            totalSize += file.getSize();
        }

        // Check if total size is reasonable (configurable limit)
        long maxTotalSize = bulkConfig != null ?
            bulkConfig.getMaxTotalSizeMb() * 1024L * 1024L :
            100L * 1024L * 1024L; // Default 100MB total

        if (totalSize > maxTotalSize) {
            logger.warn("Bulk upload total size {} bytes exceeds limit {} bytes", totalSize, maxTotalSize);

            // Return failure result for all files
            List<FileUploadResultRecord> failedResults = new ArrayList<>();
            for (MultipartFile file : files) {
                failedResults.add(FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.FILE_TOO_LARGE,
                    "Bulk upload total size exceeds limit"
                ));
            }

            return BulkUploadResultRecord.fromResults(failedResults, startTime, Instant.now());
        }

        // Process files in parallel using ExecutorService
        int maxConcurrency = bulkConfig != null ? bulkConfig.getMaxConcurrentUploads() : 3;

        // Use bounded thread pool to prevent resource exhaustion
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(maxConcurrency, Math.min(files.length, 10)) // Cap at 10 threads max
        );

        // Capture client IP in main thread before async execution
        String clientIp = getClientIp();

        try {
            // At this point, fail-fast is disabled, proceed with parallel processing
            logger.info("Bulk upload running in parallel mode - will process all files");
            return processBulkUploadParallel(files, options, clientIp, startTime, executor, bulkConfig);
        } catch (Exception e) {
            logger.error("Unexpected error in bulk upload processing", e);

            // Create error results for all files
            List<FileUploadResultRecord> errorResults = new ArrayList<>();
            for (MultipartFile file : files) {
                errorResults.add(FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.UNKNOWN_ERROR,
                    "Bulk upload processing failed: " + e.getMessage()
                ));
            }

            return BulkUploadResultRecord.fromResults(errorResults, startTime, Instant.now());

        } finally {
            // Graceful shutdown with proper cleanup
            executor.shutdown();
            try {
                // Wait for existing tasks to terminate with generous timeout
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();

                    // Wait for tasks to respond to being cancelled
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.error("Executor did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for executor termination");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Processes bulk upload with parallel processing (original logic).
     * All files are processed regardless of individual failures.
     */
    private BulkUploadResultRecord processBulkUploadParallel(
            MultipartFile[] files,
            FileUploadOptionsRecord options,
            String clientIp,
            Instant startTime,
            ExecutorService executor,
            FileManagementProperties.BulkUpload bulkConfig) {

        try {
            // Create CompletableFuture for each file
            List<CompletableFuture<FileUploadResultRecord>> futures = new ArrayList<>();

            for (MultipartFile file : files) {
                CompletableFuture<FileUploadResultRecord> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        logger.debug("Processing file {} in parallel bulk upload", file.getOriginalFilename());
                        return uploadFileInternal(file, options, clientIp);
                    } catch (Exception e) {
                        logger.error("Error processing file {} in parallel bulk upload", file.getOriginalFilename(), e);
                        return FileUploadResultRecord.error(
                            file.getOriginalFilename(),
                            FileErrorReason.UNKNOWN_ERROR,
                            "Internal error during bulk upload: " + e.getMessage()
                        );
                    }
                }, executor);

                futures.add(future);
            }

            // Wait for all futures to complete and collect results
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );

            // Wait with timeout to prevent hanging
            int timeoutSeconds = bulkConfig != null ? bulkConfig.getTimeoutSeconds() : 300;
            try {
                allFutures.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException e) {
                logger.error("Parallel bulk upload failed: {}", e.getMessage());

                // Cancel remaining futures
                futures.forEach(future -> future.cancel(true));

                // Create timeout results for files that didn't complete
                List<FileUploadResultRecord> timeoutResults = new ArrayList<>();
                for (int i = 0; i < files.length; i++) {
                    if (futures.get(i).isCancelled() || !futures.get(i).isDone()) {
                        timeoutResults.add(FileUploadResultRecord.error(
                            files[i].getOriginalFilename(),
                            FileErrorReason.BULK_UPLOAD_TIMEOUT,
                            "File upload timed out after " + timeoutSeconds + " seconds"
                        ));
                    } else {
                        try {
                            timeoutResults.add(futures.get(i).get());
                        } catch (Exception ex) {
                            timeoutResults.add(FileUploadResultRecord.error(
                                files[i].getOriginalFilename(),
                                FileErrorReason.BULK_UPLOAD_TIMEOUT,
                                "Error retrieving result: " + ex.getMessage()
                            ));
                        }
                    }
                }

                return BulkUploadResultRecord.fromResults(timeoutResults, startTime, Instant.now());
            }

            // Collect results from completed futures
            List<FileUploadResultRecord> results = new ArrayList<>();
            for (CompletableFuture<FileUploadResultRecord> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    logger.error("Error getting result from future", e);
                    // This should not happen as we already waited for all to complete
                    results.add(FileUploadResultRecord.error(
                        "unknown",
                        FileErrorReason.UNKNOWN_ERROR,
                        "Error retrieving upload result"
                    ));
                }
            }

            Instant endTime = Instant.now();
            BulkUploadResultRecord bulkResult = BulkUploadResultRecord.fromResults(results, startTime, endTime);

            logger.info("Parallel bulk upload completed: {} total, {} successful, {} failed, took {}ms",
                bulkResult.totalProcessed(), bulkResult.totalSuccess(),
                bulkResult.totalFailed(), bulkResult.processingTimeMs());

            // Log bulk operation for audit trail
            logBulkUploadAudit(bulkResult, clientIp);

            return bulkResult;

        } catch (Exception e) {
            logger.error("Error in parallel bulk upload processing", e);

            // Create error results for all files
            List<FileUploadResultRecord> errorResults = new ArrayList<>();
            for (MultipartFile file : files) {
                errorResults.add(FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.UNKNOWN_ERROR,
                    "Parallel processing error: " + e.getMessage()
                ));
            }

            return BulkUploadResultRecord.fromResults(errorResults, startTime, Instant.now());
        }
    }


    /**
     * Force fail-fast mode implementation - processes files sequentially and stops at first failure
     */
    private BulkUploadResultRecord forceFailFastMode(MultipartFile[] files, FileUploadOptionsRecord options, Instant startTime) {
        logger.info("Fail-fast mode: Starting sequential processing of {} files", files.length);

        List<FileUploadResultRecord> results = new ArrayList<>();
        String clientIp = getClientIp();

        // Process files sequentially, stopping at first failure
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            logger.debug("Fail-fast mode: Processing file {}/{}: {}", i+1, files.length, file.getOriginalFilename());

            try {
                FileUploadResultRecord result = uploadFileInternal(file, options, clientIp);
                results.add(result);

                // Check if this file failed and fail-fast is enabled
                if (!result.success()) {
                    logger.info("Fail-fast mode: File {} failed, cancelling remaining {} files",
                        file.getOriginalFilename(), files.length - i - 1);

                    // Mark all remaining files as cancelled
                    for (int j = i + 1; j < files.length; j++) {
                        results.add(FileUploadResultRecord.error(
                            files[j].getOriginalFilename(),
                            FileErrorReason.BULK_UPLOAD_CANCELLED,
                            "Upload cancelled due to fail-fast mode - previous file failed"
                        ));
                    }
                    break; // Stop processing
                }

                logger.debug("Fail-fast mode: File {} uploaded successfully", file.getOriginalFilename());

            } catch (Exception e) {
                logger.error("Fail-fast mode: Unexpected error processing file {}", file.getOriginalFilename(), e);

                // Add error result for current file
                results.add(FileUploadResultRecord.error(
                    file.getOriginalFilename(),
                    FileErrorReason.UNKNOWN_ERROR,
                    "Unexpected error: " + e.getMessage()
                ));

                // Cancel remaining files
                for (int j = i + 1; j < files.length; j++) {
                    results.add(FileUploadResultRecord.error(
                        files[j].getOriginalFilename(),
                        FileErrorReason.BULK_UPLOAD_CANCELLED,
                        "Upload cancelled due to fail-fast mode - previous file failed"
                    ));
                }
                break;
            }
        }

        Instant endTime = Instant.now();
        BulkUploadResultRecord bulkResult = BulkUploadResultRecord.fromResults(results, startTime, endTime);

        logger.info("Fail-fast mode: Completed sequential processing: {} total, {} successful, {} failed, {} cancelled, took {}ms",
            bulkResult.totalProcessed(), bulkResult.totalSuccess(),
            bulkResult.totalFailed(), bulkResult.totalCancelled(), bulkResult.processingTimeMs());

        // Log bulk operation for audit trail
        logBulkUploadAudit(bulkResult, clientIp);

        return bulkResult;
    }

    /**
     * Resolve o tamanho máximo efetivo em bytes a partir das opções e da configuração global.
     * Valores nulos, zero ou negativos resultam no fallback para a configuração do servidor.
     */
    private long resolveEffectiveMaxSizeBytes(FileUploadOptionsRecord options) {
        Long mb = (options != null) ? options.maxUploadSizeMb() : null;
        if (mb == null || mb <= 0) {
            return fileManagementProperties.getMaxFileSizeBytes();
        }
        // Protege contra overflow ao converter MB para bytes
        long maxSafeMb = Long.MAX_VALUE / (1024L * 1024L);
        if (mb > maxSafeMb) {
            logger.warn("Max upload size (MB) overflow detected: {} MB; falling back to server default.", mb);
            return fileManagementProperties.getMaxFileSizeBytes();
        }
        return mb * 1024L * 1024L;
    }
}
