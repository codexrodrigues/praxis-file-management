package br.com.praxis.filemanagement.core.validation;

import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes abrangentes para o serviço de validação de entrada
 * Valida todas as camadas de segurança e validação de dados
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Input Validation Service Tests")
public class InputValidationServiceTest {

    private InputValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new InputValidationService();
    }

    // ==================================================================================
    // TESTES DE VALIDAÇÃO BÁSICA DE ARQUIVO
    // ==================================================================================

    @Test
    @DisplayName("Should validate successful upload with valid file")
    void shouldValidateSuccessfulUploadWithValidFile() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Valid file content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertTrue(result.isValid(), "Valid file should pass validation");
        assertTrue(result.getErrors().isEmpty(), "Should have no validation errors");
    }

    @Test
    @DisplayName("Should reject null file")
    void shouldRejectNullFile() {
        // Arrange
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(null, options);

        // Assert
        assertFalse(result.isValid(), "Null file should be rejected");
        assertEquals(1, result.getErrors().size());
        assertEquals(FileErrorReason.EMPTY_FILE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("null"));
    }

    @Test
    @DisplayName("Should reject empty file")
    void shouldRejectEmptyFile() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "empty.txt",
            "text/plain",
            new byte[0] // Empty file
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "Empty file should be rejected");
        assertEquals(FileErrorReason.EMPTY_FILE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("vazio"));
    }

    @Test
    @DisplayName("Should reject file with null filename")
    void shouldRejectFileWithNullFilename() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            null, // Null filename
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "File with null filename should be rejected");
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("obrigatório"));
    }

    @Test
    @DisplayName("Should reject file with empty filename")
    void shouldRejectFileWithEmptyFilename() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "   ", // Empty/whitespace filename
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "File with empty filename should be rejected");
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
    }

    // ==================================================================================
    // TESTES DE VALIDAÇÃO DE NOME DE ARQUIVO
    // ==================================================================================

    @Test
    @DisplayName("Should reject filename that is too long")
    void shouldRejectFilenameTooLong() {
        // Arrange
        String longFilename = "a".repeat(300) + ".txt"; // Exceeds 255 characters
        MultipartFile file = new MockMultipartFile(
            "file",
            longFilename,
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "Filename too long should be rejected");
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("muito longo"));
    }

    @Test
    @DisplayName("Should detect suspicious filename patterns")
    void shouldDetectSuspiciousFilenamePatterns() {
        // Arrange - Test various suspicious patterns
        String[] suspiciousFilenames = {
            "../config.txt",      // Path traversal
            "file..exe",          // Double dots
            "%2e%2e/passwd",      // URL encoded path traversal
            "%252e%252e/config",  // Double URL encoded
            "script.exe",         // Executable extension
            "batch.bat",          // Batch file
            "virus.vbs"           // VBScript
        };

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act & Assert
        for (String filename : suspiciousFilenames) {
            MultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                "Content".getBytes()
            );

            InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

            assertFalse(result.isValid(),
                "Suspicious filename should be rejected: " + filename);
            assertEquals(FileErrorReason.SECURITY_VIOLATION, result.getPrimaryErrorReason(),
                "Should classify as security violation: " + filename);
        }
    }

    @Test
    @DisplayName("Should validate filename characters in strict mode")
    void shouldValidateFilenameCharactersInStrictMode() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "file<>:\"/\\|?*.txt", // Invalid characters
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .strictValidation(true)
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "Invalid characters should be rejected in strict mode");
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("caracteres inválidos"));
    }

    @Test
    @DisplayName("Should allow filename characters in non-strict mode")
    void shouldAllowFilenameCharactersInNonStrictMode() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document_file-v2.txt", // Valid characters
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .strictValidation(false)
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertTrue(result.isValid(), "Valid characters should be allowed in non-strict mode");
    }

    @Test
    @DisplayName("Should detect dangerous extensions in strict mode")
    void shouldDetectDangerousExtensionsInStrictMode() {
        // Arrange - Test dangerous extensions
        String[] dangerousExtensions = {
            "document.exe", "script.bat", "virus.vbs", "app.js",
            "program.py", "shell.sh", "page.php", "macro.jar"
        };

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .strictValidation(true)
            .build();

        // Act & Assert
        for (String filename : dangerousExtensions) {
            MultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                "Content".getBytes()
            );

            InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

            assertFalse(result.isValid(),
                "Dangerous extension should be rejected in strict mode: " + filename);
            assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        }
    }

    @Test
    @DisplayName("Should detect Windows reserved names")
    void shouldDetectWindowsReservedNames() {
        String[] reservedNames = {
            "CON.txt", "PRN.doc", "AUX.pdf", "NUL.log",
            "COM1.txt", "COM9.doc", "LPT1.pdf", "LPT9.log"
        };

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder().build();

        for (String filename : reservedNames) {
            MultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                "Content".getBytes()
            );

            InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

            assertFalse(result.isValid(),
                "Windows reserved name should be rejected: " + filename);
            assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        }
    }

    @Test
    @DisplayName("Should reject filenames with dangerous unicode characters")
    void shouldRejectDangerousUnicodeFilenames() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "evil‮txt",
            "text/plain",
            "data".getBytes()
        );

        InputValidationService.ValidationResult result =
            validationService.validateUploadFile(file, FileUploadOptionsRecord.builder().build());

        assertFalse(result.isValid());
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
    }

    // ==================================================================================
    // TESTES DE VALIDAÇÃO DE OPÇÕES
    // ==================================================================================

    @Test
    @DisplayName("Should validate upload options with valid parameters")
    void shouldValidateUploadOptionsWithValidParameters() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .maxUploadSizeMb(50L)
            .acceptMimeTypes(List.of("text/plain", "application/pdf"))
            .nameConflictPolicy(NameConflictPolicy.RENAME)
            .strictValidation(true)
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertTrue(result.isValid(), "Valid options should pass validation");
    }

    @Test
    @DisplayName("Should reject excessive max upload size")
    void shouldRejectExcessiveMaxUploadSize() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .maxUploadSizeMb(2000L) // Exceeds 1GB limit
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "Excessive max upload size should be rejected");
        assertEquals(FileErrorReason.FILE_TOO_LARGE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("muito grande"));
    }

    @Test
    @DisplayName("Should reject invalid MIME types in options")
    void shouldRejectInvalidMimeTypesInOptions() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .acceptMimeTypes(List.of("text/plain", "invalid-mime-type", "application/pdf"))
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "Invalid MIME types should be rejected");
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("MIME type inválido"));
    }

    // ==================================================================================
    // TESTES DE VALIDAÇÃO DE SEGURANÇA
    // ==================================================================================

    @Test
    @DisplayName("Should detect path traversal attempts")
    void shouldDetectPathTraversalAttempts() {
        // Arrange - Various path traversal patterns
        String[] pathTraversalFilenames = {
            "../../../etc/passwd",
            "..\\..\\windows\\system32\\config",
            "./../../sensitive/data.txt"
        };

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act & Assert
        for (String filename : pathTraversalFilenames) {
            MultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/plain",
                "Content".getBytes()
            );

            InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

            assertFalse(result.isValid(),
                "Path traversal should be detected: " + filename);
            assertEquals(FileErrorReason.SECURITY_VIOLATION, result.getPrimaryErrorReason());
            assertTrue(result.getErrorSummary().contains("path traversal"));
        }
    }

    @Test
    @DisplayName("Should detect control characters in strict mode")
    void shouldDetectControlCharactersInStrictMode() {
        // Arrange
        String filenameWithControlChars = "file\u0000\u0001.txt"; // Null and control characters
        MultipartFile file = new MockMultipartFile(
            "file",
            filenameWithControlChars,
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .strictValidation(true)
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "Control characters should be detected in strict mode");
        // Control characters are detected through the filename pattern validation
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("caracteres inválidos"),
            "Should detect invalid characters in filename");
    }

    @Test
    @DisplayName("Should detect MIME type and extension mismatch in strict mode")
    void shouldDetectMimeTypeExtensionMismatchInStrictMode() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "application/pdf", // PDF MIME type with .txt extension
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .strictValidation(true)
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertFalse(result.isValid(), "MIME type mismatch should be detected in strict mode");
        assertEquals(FileErrorReason.SECURITY_VIOLATION, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("Incompatibilidade"));
    }

    @Test
    @DisplayName("Should allow MIME type and extension mismatch in non-strict mode")
    void shouldAllowMimeTypeExtensionMismatchInNonStrictMode() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "application/pdf", // PDF MIME type with .txt extension
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .strictValidation(false)
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertTrue(result.isValid(), "MIME type mismatch should be allowed in non-strict mode");
    }

    // ==================================================================================
    // TESTES DE VALIDAÇÃO DE DIRETÓRIO
    // ==================================================================================


    @Test
    @DisplayName("Should reject null directory path")
    void shouldRejectNullDirectoryPath() {
        // Act
        InputValidationService.ValidationResult result = validationService.validateDirectoryPath(null);

        // Assert
        assertFalse(result.isValid(), "Null path should be rejected");
        assertEquals(FileErrorReason.INVALID_PATH, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("vazio"));
    }

    @Test
    @DisplayName("Should reject empty directory path")
    void shouldRejectEmptyDirectoryPath() {
        // Act
        InputValidationService.ValidationResult result = validationService.validateDirectoryPath("   ");

        // Assert
        assertFalse(result.isValid(), "Empty path should be rejected");
        assertEquals(FileErrorReason.INVALID_PATH, result.getPrimaryErrorReason());
    }

    @Test
    @DisplayName("Should reject directory path that is too long")
    void shouldRejectDirectoryPathTooLong() {
        // Arrange
        String longPath = "/home/" + "a".repeat(5000); // Exceeds max path length

        // Act
        InputValidationService.ValidationResult result = validationService.validateDirectoryPath(longPath);

        // Assert
        assertFalse(result.isValid(), "Path too long should be rejected");
        assertEquals(FileErrorReason.INVALID_PATH, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("muito longo"));
    }

    @Test
    @DisplayName("Should reject directory path with suspicious characters")
    void shouldRejectDirectoryPathWithSuspiciousCharacters() {
        // Arrange - Test suspicious patterns
        String[] suspiciousPaths = {
            "/home/../../../etc/passwd",  // Path traversal
            "/home/user/~/sensitive",     // Home directory expansion
            "../uploads"                  // Relative path with traversal
        };

        // Act & Assert
        for (String path : suspiciousPaths) {
            InputValidationService.ValidationResult result = validationService.validateDirectoryPath(path);

            assertFalse(result.isValid(),
                "Suspicious path should be rejected: " + path);
            assertEquals(FileErrorReason.SECURITY_VIOLATION, result.getPrimaryErrorReason());
        }
    }

    @Test
    @DisplayName("Should reject relative directory paths")
    void shouldRejectRelativeDirectoryPaths() {
        // Arrange
        String relativePath = "uploads/files"; // Relative path

        // Act
        InputValidationService.ValidationResult result = validationService.validateDirectoryPath(relativePath);

        // Assert
        assertFalse(result.isValid(), "Relative path should be rejected");
        assertEquals(FileErrorReason.INVALID_PATH, result.getPrimaryErrorReason());
        assertTrue(result.getErrorSummary().contains("absoluto"));
    }

    // ==================================================================================
    // TESTES DE UTILITÁRIOS ESTÁTICOS
    // ==================================================================================

    @Test
    @DisplayName("Should validate non-null parameters")
    void shouldValidateNonNullParameters() {
        // Act & Assert
        assertDoesNotThrow(() -> InputValidationService.requireNonNull("valid", "param"));
        assertThrows(IllegalArgumentException.class, () ->
            InputValidationService.requireNonNull(null, "param"));
    }

    @Test
    @DisplayName("Should validate non-empty strings")
    void shouldValidateNonEmptyStrings() {
        // Act & Assert
        assertDoesNotThrow(() -> InputValidationService.requireNonEmpty("valid", "param"));
        assertThrows(IllegalArgumentException.class, () ->
            InputValidationService.requireNonEmpty("", "param"));
        assertThrows(IllegalArgumentException.class, () ->
            InputValidationService.requireNonEmpty("   ", "param"));
        assertThrows(IllegalArgumentException.class, () ->
            InputValidationService.requireNonEmpty(null, "param"));
    }

    @Test
    @DisplayName("Should validate positive numbers")
    void shouldValidatePositiveNumbers() {
        // Act & Assert
        assertDoesNotThrow(() -> InputValidationService.requirePositive(1L, "param"));
        assertDoesNotThrow(() -> InputValidationService.requirePositive(100L, "param"));
        assertThrows(IllegalArgumentException.class, () ->
            InputValidationService.requirePositive(0L, "param"));
        assertThrows(IllegalArgumentException.class, () ->
            InputValidationService.requirePositive(-1L, "param"));
    }

    // ==================================================================================
    // TESTES DE RESULTADO DE VALIDAÇÃO
    // ==================================================================================

    @Test
    @DisplayName("Should create success validation result")
    void shouldCreateSuccessValidationResult() {
        // Act
        InputValidationService.ValidationResult result = InputValidationService.ValidationResult.success();

        // Assert
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertEquals("Validation failed", result.getErrorSummary()); // Default when no errors
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason()); // Default when no errors
    }

    @Test
    @DisplayName("Should create failure validation result")
    void shouldCreateFailureValidationResult() {
        // Arrange
        InputValidationService.ValidationError error = new InputValidationService.ValidationError(
            "filename", "Invalid filename", FileErrorReason.INVALID_TYPE, "test.exe"
        );

        // Act
        InputValidationService.ValidationResult result = InputValidationService.ValidationResult.failure(error);

        // Assert
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("Invalid filename", result.getErrorSummary());
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason());
    }

    @Test
    @DisplayName("Should create validation error with all properties")
    void shouldCreateValidationErrorWithAllProperties() {
        // Act
        InputValidationService.ValidationError error = new InputValidationService.ValidationError(
            "filename", "Test error message", FileErrorReason.SECURITY_VIOLATION, "malicious.exe"
        );

        // Assert
        assertEquals("filename", error.getField());
        assertEquals("Test error message", error.getMessage());
        assertEquals(FileErrorReason.SECURITY_VIOLATION, error.getErrorReason());
        assertEquals("malicious.exe", error.getInvalidValue());
    }

    @Test
    @DisplayName("Should concatenate multiple error messages")
    void shouldConcatenateMultipleErrorMessages() {
        // Arrange
        List<InputValidationService.ValidationError> errors = List.of(
            new InputValidationService.ValidationError("field1", "Error 1", FileErrorReason.INVALID_TYPE, null),
            new InputValidationService.ValidationError("field2", "Error 2", FileErrorReason.SECURITY_VIOLATION, null),
            new InputValidationService.ValidationError("field3", "Error 3", FileErrorReason.FILE_TOO_LARGE, null)
        );

        // Act
        InputValidationService.ValidationResult result = InputValidationService.ValidationResult.failure(errors);

        // Assert
        assertFalse(result.isValid());
        assertEquals(3, result.getErrors().size());
        assertEquals("Error 1; Error 2; Error 3", result.getErrorSummary());
        assertEquals(FileErrorReason.INVALID_TYPE, result.getPrimaryErrorReason()); // First error
    }

    @Test
    @DisplayName("Should accept non-positive max upload size as fallback to server default")
    void shouldAcceptNonPositiveMaxUploadSizeAsFallback() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Content".getBytes()
        );
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .maxUploadSizeMb(-10L) // Non-positive should be treated as fallback
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertTrue(result.isValid(), "Non-positive maxUploadSizeMb should not cause validation error");
    }

    @Test
    @DisplayName("Should accept negative max upload size as fallback (no exception)")
    void shouldAcceptNegativeMaxUploadSizeAsFallbackNoException() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Content".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .maxUploadSizeMb(-10L) // Negative should be treated as fallback
            .build();

        // Act
        InputValidationService.ValidationResult result = validationService.validateUploadFile(file, options);

        // Assert
        assertTrue(result.isValid(), "Negative maxUploadSizeMb should not cause validation error");
    }
}
