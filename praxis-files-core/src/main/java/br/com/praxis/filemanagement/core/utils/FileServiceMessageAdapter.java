package br.com.praxis.filemanagement.core.utils;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;

import java.util.Map;

/**
 * Adaptador para integrar o sistema de mensagens padronizadas com o core do sistema
 * Este adaptador permite que o módulo core use as mensagens padronizadas
 */
public class FileServiceMessageAdapter {

    // ==================== Record-based methods ====================

    /**
     * Cria FileUploadResultRecord com mensagem de erro padronizada
     */
    public static FileUploadResultRecord createStandardErrorRecord(FileErrorReason errorReason, String originalFilename, long fileSize) {
        Map<String, Object> errorResponse = ErrorMessageUtils.createStandardErrorResponse(errorReason, originalFilename, fileSize);
        return FileUploadResultRecord.error(originalFilename, errorReason, (String) errorResponse.get("message"), fileSize);
    }

    /**
     * Cria FileUploadResultRecord com mensagem de erro genérica padronizada
     */
    public static FileUploadResultRecord createGenericErrorRecord(String errorKey, String originalFilename, long fileSize) {
        Map<String, Object> errorResponse = ErrorMessageUtils.createGenericErrorResponseByKey(errorKey, originalFilename, fileSize);
        return FileUploadResultRecord.error(originalFilename, FileErrorReason.UNKNOWN_ERROR, (String) errorResponse.get("message"), fileSize);
    }

    /**
     * Cria FileUploadResultRecord com mensagem de erro de tamanho de arquivo excedido
     */
    public static FileUploadResultRecord createFileSizeExceededRecord(long fileSize, long maxSizeMb, String originalFilename) {
        Map<String, Object> errorResponse = ErrorMessageUtils.createFileSizeExceededResponse(fileSize, maxSizeMb, originalFilename);
        return FileUploadResultRecord.error(originalFilename, FileErrorReason.FILE_TOO_LARGE, (String) errorResponse.get("message"), fileSize);
    }

    /**
     * Cria FileUploadResultRecord com mensagem de erro de malware detectado
     */
    public static FileUploadResultRecord createMalwareDetectedRecord(String virusName, String originalFilename, long fileSize) {
        Map<String, Object> errorResponse = ErrorMessageUtils.createMalwareDetectedResponse(virusName, originalFilename, fileSize);
        return FileUploadResultRecord.error(originalFilename, FileErrorReason.MALWARE_DETECTED, (String) errorResponse.get("message"), fileSize);
    }

    // Métodos de conveniência para Record-based específicos

    public static FileUploadResultRecord createRateLimitExceededRecord(String originalFilename, long fileSize) {
        Map<String, Object> errorResponse = ErrorMessageUtils.createGenericErrorResponseByKey("RATE_LIMIT_EXCEEDED", originalFilename, fileSize);
        return FileUploadResultRecord.error(originalFilename, FileErrorReason.RATE_LIMIT_EXCEEDED, (String) errorResponse.get("message"), fileSize);
    }

    public static FileUploadResultRecord createEmptyFileRecord(String originalFilename, long fileSize) {
        return createStandardErrorRecord(FileErrorReason.EMPTY_FILE, originalFilename, fileSize);
    }

    public static FileUploadResultRecord createConfigurationErrorRecord(String originalFilename, long fileSize) {
        return createGenericErrorRecord("CONFIGURATION_ERROR", originalFilename, fileSize);
    }

    public static FileUploadResultRecord createFileAnalysisErrorRecord(String originalFilename, long fileSize) {
        return createGenericErrorRecord("FILE_ANALYSIS_ERROR", originalFilename, fileSize);
    }

    public static FileUploadResultRecord createFileTypeNotAllowedRecord(String originalFilename, long fileSize) {
        return createStandardErrorRecord(FileErrorReason.INVALID_TYPE, originalFilename, fileSize);
    }

    public static FileUploadResultRecord createVirusScanErrorRecord(String originalFilename, long fileSize) {
        return createGenericErrorRecord("VIRUS_SCAN_ERROR", originalFilename, fileSize);
    }

    public static FileUploadResultRecord createFileStructureErrorRecord(String originalFilename, long fileSize) {
        return createGenericErrorRecord("FILE_STRUCTURE_ERROR", originalFilename, fileSize);
    }

    public static FileUploadResultRecord createFileExistsRecord(String originalFilename, long fileSize) {
        return createStandardErrorRecord(FileErrorReason.FILE_EXISTS, originalFilename, fileSize);
    }

    public static FileUploadResultRecord createPathTraversalRecord(String originalFilename, long fileSize) {
        return createStandardErrorRecord(FileErrorReason.INVALID_PATH, originalFilename, fileSize);
    }

    public static FileUploadResultRecord createFileStoreErrorRecord(String originalFilename, long fileSize) {
        return createGenericErrorRecord("FILE_STORE_ERROR", originalFilename, fileSize);
    }
}
