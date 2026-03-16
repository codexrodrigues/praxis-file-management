package br.com.praxis.filemanagement.core.utils;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para utilitário de mensagens de erro
 * Valida mapeamentos e conversões de erro
 */
@DisplayName("Error Message Utils Tests")
class ErrorMessageUtilsTest {
    
    // ==================================================================================
    // TESTES DE MAPEAMENTO DE ERROS ESPECÍFICOS
    // ==================================================================================
    
    @Test
    @DisplayName("Should map SUSPICIOUS_EXTENSION_BLOCKED correctly")
    void shouldMapSuspiciousExtensionBlocked() {
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED);
        
        // Assert
        assertNotNull(errorInfo);
        assertEquals("EXTENSAO_SUSPEITA_BLOQUEADA", errorInfo.getCode());
        assertEquals("Extensão de arquivo suspeita foi bloqueada", errorInfo.getMessage());
        assertTrue(errorInfo.getDetails().contains("perigosa"));
    }
    
    @Test
    @DisplayName("Should map VIRUS_SCAN_UNAVAILABLE correctly")
    void shouldMapVirusScanUnavailable() {
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(FileErrorReason.VIRUS_SCAN_UNAVAILABLE);
        
        // Assert
        assertNotNull(errorInfo);
        assertEquals("SCANNER_VIRUS_INDISPONIVEL", errorInfo.getCode());
        assertEquals("Scanner de vírus obrigatório não está disponível", errorInfo.getMessage());
        assertTrue(errorInfo.getDetails().contains("obrigatório"));
    }
    
    @Test
    @DisplayName("Should map MAGIC_NUMBER_MISMATCH correctly")
    void shouldMapMagicNumberMismatch() {
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(FileErrorReason.MAGIC_NUMBER_MISMATCH);
        
        // Assert
        assertNotNull(errorInfo);
        assertEquals("MAGIC_NUMBER_INCOMPATIVEL", errorInfo.getCode());
        assertEquals("Magic number não corresponde ao tipo de arquivo", errorInfo.getMessage());
        assertTrue(errorInfo.getDetails().contains("assinatura"));
    }
    
    @Test
    @DisplayName("Should map DANGEROUS_EXECUTABLE correctly")
    void shouldMapDangerousExecutable() {
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(FileErrorReason.DANGEROUS_EXECUTABLE);
        
        // Assert
        assertNotNull(errorInfo);
        assertEquals("ARQUIVO_EXECUTAVEL_PERIGOSO", errorInfo.getCode());
        assertEquals("Arquivo executável detectado", errorInfo.getMessage());
        assertTrue(errorInfo.getDetails().contains("executáveis"));
    }
    
    @Test
    @DisplayName("Should map FILE_TOO_LARGE correctly")
    void shouldMapFileTooLarge() {
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(FileErrorReason.FILE_TOO_LARGE);
        
        // Assert
        assertNotNull(errorInfo);
        assertEquals("ARQUIVO_MUITO_GRANDE", errorInfo.getCode());
        assertEquals("Arquivo excede o tamanho máximo permitido", errorInfo.getMessage());
        assertTrue(errorInfo.getDetails().contains("tamanho"));
    }
    
    @Test
    @DisplayName("Should return UNKNOWN_ERROR for unmapped reasons")
    void shouldReturnUnknownErrorForUnmapped() {
        // Arrange - usando um valor que pode não estar mapeado ou null
        FileErrorReason unmappedReason = null;
        
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(unmappedReason);
        
        // Assert
        assertNotNull(errorInfo);
        assertEquals("ERRO_DESCONHECIDO", errorInfo.getCode());
        assertEquals("Erro interno do sistema", errorInfo.getMessage());
        assertTrue(errorInfo.getDetails().contains("suporte"));
    }
    
    // ==================================================================================
    // TESTES DE CONVERSÃO DE FileUploadResultRecord
    // ==================================================================================
    
    @Test
    @DisplayName("Should convert successful FileUploadResultRecord to standard format")
    void shouldConvertSuccessfulResult() {
        // Arrange
        FileUploadResultRecord result = FileUploadResultRecord.success(
            "document.txt",
            "server-123.txt",
            "file-id-456",
            1024L,
            "text/plain"
        );
        
        // Act
        Map<String, Object> response = ErrorMessageUtils.convertFileUploadResultRecordToStandardFormat(result);
        
        // Assert
        assertNotNull(response);
        assertEquals(true, response.get("success"));
        assertNotNull(response.get("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data);
        assertEquals("document.txt", data.get("originalFilename"));
        assertEquals("server-123.txt", data.get("serverFilename"));
        assertEquals("file-id-456", data.get("fileId"));
        assertEquals(1024L, data.get("fileSize"));
        assertEquals("text/plain", data.get("mimeType"));
    }
    
    @Test
    @DisplayName("Should convert error FileUploadResultRecord to standard format")
    void shouldConvertErrorResult() {
        // Arrange
        FileUploadResultRecord result = FileUploadResultRecord.error(
            "malicious.exe",
            FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED,
            "Extensão suspeita bloqueada"
        );
        
        // Act
        Map<String, Object> response = ErrorMessageUtils.convertFileUploadResultRecordToStandardFormat(result);
        
        // Assert
        assertNotNull(response);
        assertEquals(false, response.get("success"));
        assertEquals("EXTENSAO_SUSPEITA_BLOQUEADA", response.get("code"));
        assertEquals("Extensão de arquivo suspeita foi bloqueada", response.get("message"));
        assertTrue(((String) response.get("details")).contains("perigosa"));
        assertNotNull(response.get("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data);
        assertEquals("malicious.exe", data.get("originalFilename"));
        assertEquals(0L, data.get("fileSize"));
    }
    
    // ==================================================================================
    // TESTES DE MENSAGENS PADRONIZADAS
    // ==================================================================================
    
    @Test
    @DisplayName("Should create standard error response")
    void shouldCreateStandardErrorResponse() {
        // Act
        Map<String, Object> response = ErrorMessageUtils.createStandardErrorResponse(
            FileErrorReason.VIRUS_SCAN_UNAVAILABLE,
            "test.exe",
            2048L
        );
        
        // Assert
        assertNotNull(response);
        assertEquals("SCANNER_VIRUS_INDISPONIVEL", response.get("code"));
        assertEquals("Scanner de vírus obrigatório não está disponível", response.get("message"));
        assertNotNull(response.get("timestamp"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("test.exe", data.get("originalFilename"));
        assertEquals(2048L, data.get("fileSize"));
    }
    
    @Test
    @DisplayName("Should create generic error response")
    void shouldCreateGenericErrorResponse() {
        // Act
        Map<String, Object> response = ErrorMessageUtils.createGenericErrorResponse(
            "CUSTOM_ERROR",
            "Custom error message",
            "Custom error details"
        );
        
        // Assert
        assertNotNull(response);
        assertEquals("CUSTOM_ERROR", response.get("code"));
        assertEquals("Custom error message", response.get("message"));
        assertEquals("Custom error details", response.get("details"));
        assertNotNull(response.get("timestamp"));
    }

    @Test
    @DisplayName("Should expose merged message catalog and generic fallback")
    void shouldExposeMergedMessageCatalogAndGenericFallback() {
        Map<String, String> messages = ErrorMessageUtils.getAllMessages();
        ErrorMessageUtils.ErrorInfo genericFallback = ErrorMessageUtils.getGenericErrorInfo("DOES_NOT_EXIST");

        assertTrue(messages.containsKey("EMPTY_FILE"));
        assertTrue(messages.containsKey("RATE_LIMIT_EXCEEDED"));
        assertEquals("ERRO_DESCONHECIDO", genericFallback.getCode());
        assertEquals("Erro interno do sistema", genericFallback.getMessage());
    }

    @Test
    @DisplayName("Should create specialized file size and malware responses")
    void shouldCreateSpecializedResponses() {
        Map<String, Object> sizeResponse = ErrorMessageUtils.createFileSizeExceededResponse(2048L, 1L, "big.txt");
        Map<String, Object> malwareResponse = ErrorMessageUtils.createMalwareDetectedResponse("EICAR", "bad.txt", 99L);

        assertEquals("ARQUIVO_MUITO_GRANDE", sizeResponse.get("code"));
        assertEquals("big.txt", sizeResponse.get("originalFilename"));
        assertEquals(1L, sizeResponse.get("maxSizeMb"));

        assertEquals("MALWARE_DETECTADO", malwareResponse.get("code"));
        assertEquals("EICAR", malwareResponse.get("virusName"));
        assertEquals(99L, malwareResponse.get("fileSize"));
    }
    
    // ==================================================================================
    // TESTES DE CASOS ESPECÍFICOS
    // ==================================================================================
    
    
    
    // ==================================================================================
    // TESTES DE VALIDAÇÃO DE ESTRUTURA
    // ==================================================================================
    
    @Test
    @DisplayName("Should validate ErrorInfo structure")
    void shouldValidateErrorInfoStructure() {
        // Act
        ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(FileErrorReason.EMPTY_FILE);
        
        // Assert
        assertNotNull(errorInfo);
        assertNotNull(errorInfo.getCode());
        assertNotNull(errorInfo.getMessage());
        assertNotNull(errorInfo.getDetails());
        assertFalse(errorInfo.getCode().isEmpty());
        assertFalse(errorInfo.getMessage().isEmpty());
        assertFalse(errorInfo.getDetails().isEmpty());
    }
    
    @Test
    @DisplayName("Should have consistent error structure for all mapped errors")
    void shouldHaveConsistentErrorStructure() {
        // Arrange
        FileErrorReason[] criticalErrors = {
            FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED,
            FileErrorReason.VIRUS_SCAN_UNAVAILABLE,
            FileErrorReason.MAGIC_NUMBER_MISMATCH,
            FileErrorReason.DANGEROUS_EXECUTABLE,
            FileErrorReason.DANGEROUS_SCRIPT,
            FileErrorReason.MALWARE_DETECTED,
            FileErrorReason.FILE_TOO_LARGE,
            FileErrorReason.EMPTY_FILE
        };
        
        // Act & Assert
        for (FileErrorReason errorReason : criticalErrors) {
            ErrorMessageUtils.ErrorInfo errorInfo = ErrorMessageUtils.getErrorInfo(errorReason);
            
            assertNotNull(errorInfo, "ErrorInfo should not be null for " + errorReason);
            assertNotNull(errorInfo.getCode(), "Error code should not be null for " + errorReason);
            assertNotNull(errorInfo.getMessage(), "Error message should not be null for " + errorReason);
            assertNotNull(errorInfo.getDetails(), "Error details should not be null for " + errorReason);
            
            assertFalse(errorInfo.getCode().isEmpty(), "Error code should not be empty for " + errorReason);
            assertFalse(errorInfo.getMessage().isEmpty(), "Error message should not be empty for " + errorReason);
            assertFalse(errorInfo.getDetails().isEmpty(), "Error details should not be empty for " + errorReason);
        }
    }
}
