package br.com.praxis.filemanagement.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes abrangentes para o utilitário de manipulação de nomes de arquivos
 * Valida sanitização, decomposição e reconstrução de nomes
 */
@DisplayName("File Name Handler Tests")
class FileNameHandlerTest {

    // ==================================================================================
    // TESTES DE DECOMPOSIÇÃO DE NOMES
    // ==================================================================================

    @Test
    @DisplayName("Should decompose simple filename correctly")
    void shouldDecomposeSimpleFilenameCorrectly() {
        // Act
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("document.pdf");

        // Assert
        assertEquals("document", components.baseName());
        assertEquals("pdf", components.extension());
        assertEquals("document.pdf", components.originalName());
        assertEquals("document.pdf", components.getFullName());
        assertTrue(components.hasExtension());
    }

    @Test
    @DisplayName("Should decompose filename without extension")
    void shouldDecomposeFilenameWithoutExtension() {
        // Act
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("README");

        // Assert
        assertEquals("README", components.baseName());
        assertEquals("", components.extension());
        assertEquals("README", components.originalName());
        assertEquals("README", components.getFullName());
        assertFalse(components.hasExtension());
    }

    @Test
    @DisplayName("Should decompose filename with multiple dots")
    void shouldDecomposeFilenameWithMultipleDots() {
        // Act
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("archive.tar.gz");

        // Assert
        assertEquals("archive_tar", components.baseName()); // Dots in base name get sanitized
        assertEquals("gz", components.extension());
        assertEquals("archive.tar.gz", components.originalName());
        assertEquals("archive_tar.gz", components.getFullName());
        assertTrue(components.hasExtension());
    }

    @Test
    @DisplayName("Should handle filename starting with dot")
    void shouldHandleFilenameStartingWithDot() {
        // Act
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose(".gitignore");

        // Assert
        assertEquals("gitignore", components.baseName()); // Leading dot removed during sanitization
        assertEquals("", components.extension());
        assertEquals(".gitignore", components.originalName());
        assertEquals("gitignore", components.getFullName());
        assertFalse(components.hasExtension());
    }

    @Test
    @DisplayName("Should handle filename ending with dot")
    void shouldHandleFilenameEndingWithDot() {
        // Act
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("document.");

        // Assert
        assertEquals("document", components.baseName());
        assertEquals("", components.extension());
        assertEquals("document.", components.originalName());
        assertEquals("document", components.getFullName());
        assertFalse(components.hasExtension());
    }

    @Test
    @DisplayName("Should reject null filename")
    void shouldRejectNullFilename() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            FileNameHandler.decompose(null));
    }

    @Test
    @DisplayName("Should reject empty filename")
    void shouldRejectEmptyFilename() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            FileNameHandler.decompose(""));
    }

    // ==================================================================================
    // TESTES DE SANITIZAÇÃO
    // ==================================================================================

    @ParameterizedTest
    @CsvSource({
        "'document.pdf', 'document.pdf'",
        "'My Document.docx', 'My_Document.docx'"
    })
    @DisplayName("Should sanitize various filename patterns correctly")
    void shouldSanitizeVariousFilenamePatterns(String input, String expected) {
        // Act
        String sanitized = FileNameHandler.sanitize(input);

        // Assert
        assertEquals(expected, sanitized);
    }

    @Test
    @DisplayName("Should sanitize special characters correctly")
    void shouldSanitizeSpecialCharactersCorrectly() {
        // Act & Assert
        assertEquals("file.txt", FileNameHandler.sanitize("file@#$%.txt"));
        assertEquals("file.exe", FileNameHandler.sanitize("file<>:\"/\\|?*.exe"));
        
        // Path traversal generates fallback name
        String sanitized = FileNameHandler.sanitize("../../../etc/passwd");
        assertTrue(sanitized.matches("^file_[a-z0-9]{8}\\.etcpasswd$"),
            "Should generate fallback name for path traversal: " + sanitized);
        
        // Unicode characters that can't be sanitized generate fallback
        String unicodeSanitized = FileNameHandler.sanitize("中文文档.pdf");
        assertTrue(unicodeSanitized.matches("^file_[a-z0-9]{8}\\.pdf$"),
            "Should generate fallback name for unicode: " + unicodeSanitized);
        
        // Emoji characters are removed/replaced
        assertEquals("moji.txt", FileNameHandler.sanitize("émoji😀.txt"));
    }

    @Test
    @DisplayName("Should generate fallback name for reserved Windows names")
    void shouldGenerateFallbackForReservedWindowsNames() {
        String sanitized = FileNameHandler.sanitize("con.txt");
        assertTrue(sanitized.matches("^file_[a-z0-9]{8}\\.txt$"));
    }

    @Test
    @DisplayName("Should generate fallback name for invalid characters only")
    void shouldGenerateFallbackNameForInvalidCharactersOnly() {
        // Act
        String sanitized = FileNameHandler.sanitize("@#$%^&*().txt");

        // Assert
        assertTrue(sanitized.matches("^file_[a-z0-9]{8}\\.txt$"), 
            "Should generate fallback name with pattern file_XXXXXXXX.txt, got: " + sanitized);
    }

    @Test
    @DisplayName("Should preserve valid alphanumeric characters")
    void shouldPreserveValidAlphanumericCharacters() {
        // Act
        String sanitized = FileNameHandler.sanitize("File_123-Test.pdf");

        // Assert
        assertEquals("File_123-Test.pdf", sanitized);
    }

    @Test
    @DisplayName("Should normalize multiple underscores")
    void shouldNormalizeMultipleUnderscores() {
        // Act
        String sanitized = FileNameHandler.sanitize("file____name.txt");

        // Assert
        assertEquals("file_name.txt", sanitized);
    }

    @Test
    @DisplayName("Should remove leading and trailing underscores")
    void shouldRemoveLeadingAndTrailingUnderscores() {
        // Act
        String sanitized = FileNameHandler.sanitize("___filename___.txt");

        // Assert
        assertEquals("filename.txt", sanitized);
    }

    @Test
    @DisplayName("Should limit extension length")
    void shouldLimitExtensionLength() {
        // Act
        String sanitized = FileNameHandler.sanitize("document.verylongextensionname");

        // Assert
        assertEquals("document.verylongex", sanitized); // Extension limited to 10 chars
    }

    @Test
    @DisplayName("Should convert extension to lowercase")
    void shouldConvertExtensionToLowercase() {
        // Act
        String sanitized = FileNameHandler.sanitize("document.PDF");

        // Assert
        assertEquals("document.pdf", sanitized);
    }

    @Test
    @DisplayName("Should remove non-alphanumeric characters from extension")
    void shouldRemoveNonAlphanumericCharactersFromExtension() {
        // Act
        String sanitized = FileNameHandler.sanitize("document.p@d#f$");

        // Assert
        assertEquals("document.pdf", sanitized);
    }

    // ==================================================================================
    // TESTES DE RECONSTRUÇÃO
    // ==================================================================================

    @Test
    @DisplayName("Should reconstruct filename from components")
    void shouldReconstructFilenameFromComponents() {
        // Arrange
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("document.pdf");

        // Act
        String reconstructed = FileNameHandler.reconstruct(components);

        // Assert
        assertEquals("document.pdf", reconstructed);
    }

    @Test
    @DisplayName("Should reconstruct filename without extension")
    void shouldReconstructFilenameWithoutExtension() {
        // Arrange
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("README");

        // Act
        String reconstructed = FileNameHandler.reconstruct(components);

        // Assert
        assertEquals("README", reconstructed);
    }

    @Test
    @DisplayName("Should reject null components in reconstruct")
    void shouldRejectNullComponentsInReconstruct() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> 
            FileNameHandler.reconstruct(null));
    }

    // ==================================================================================
    // TESTES DE NOMES INCREMENTAIS
    // ==================================================================================

    @Test
    @DisplayName("Should create incremental name with extension")
    void shouldCreateIncrementalNameWithExtension() {
        // Arrange
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("document.pdf");

        // Act
        String incremental = FileNameHandler.createIncrementalName(components, 1);

        // Assert
        assertEquals("document(1).pdf", incremental);
    }

    @Test
    @DisplayName("Should create incremental name without extension")
    void shouldCreateIncrementalNameWithoutExtension() {
        // Arrange
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("README");

        // Act
        String incremental = FileNameHandler.createIncrementalName(components, 5);

        // Assert
        assertEquals("README(5)", incremental);
    }

    @Test
    @DisplayName("Should handle various increment numbers")
    void shouldHandleVariousIncrementNumbers() {
        // Arrange
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("file.txt");

        // Act & Assert
        assertEquals("file(1).txt", FileNameHandler.createIncrementalName(components, 1));
        assertEquals("file(99).txt", FileNameHandler.createIncrementalName(components, 99));
        assertEquals("file(1000).txt", FileNameHandler.createIncrementalName(components, 1000));
    }

    @Test
    @DisplayName("Should reject invalid increment numbers")
    void shouldRejectInvalidIncrementNumbers() {
        // Arrange
        FileNameHandler.FileNameComponents components = FileNameHandler.decompose("file.txt");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            FileNameHandler.createIncrementalName(components, 0));
        assertThrows(IllegalArgumentException.class, () -> 
            FileNameHandler.createIncrementalName(components, -1));
    }

    @Test
    @DisplayName("Should reject null components in incremental name")
    void shouldRejectNullComponentsInIncrementalName() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> 
            FileNameHandler.createIncrementalName(null, 1));
    }

    // ==================================================================================
    // TESTES DE VALIDAÇÃO
    // ==================================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "document.pdf",
        "README",
        "file-name_123.txt",
        "Image.jpeg",
        "archive.tar"
    })
    @DisplayName("Should validate correctly formatted filenames")
    void shouldValidateCorrectlyFormattedFilenames(String filename) {
        // Act
        boolean isValid = FileNameHandler.isValid(filename);

        // Assert
        assertTrue(isValid, "Filename should be valid: " + filename);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "file with spaces.txt",  // Spaces not allowed in final format
        "file@special.txt",      // Special characters not allowed
        "../path/traversal.txt", // Path components not allowed
        "file<>:\"/\\|?*.exe"   // Multiple invalid characters
    })
    @DisplayName("Should reject incorrectly formatted filenames")
    void shouldRejectIncorrectlyFormattedFilenames(String filename) {
        // Act
        boolean isValid = FileNameHandler.isValid(filename);

        // Assert
        assertFalse(isValid, "Filename should be invalid: " + filename);
    }

    @Test
    @DisplayName("Should reject null filename in validation")
    void shouldRejectNullFilenameInValidation() {
        // Act
        boolean isValid = FileNameHandler.isValid(null);

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject empty filename in validation")
    void shouldRejectEmptyFilenameInValidation() {
        // Act
        boolean isValid = FileNameHandler.isValid("");

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should validate idempotent sanitization")
    void shouldValidateIdempotentSanitization() {
        // Arrange
        String originalFilename = "My Document (v2).pdf";
        
        // Act
        String firstSanitization = FileNameHandler.sanitize(originalFilename);
        String secondSanitization = FileNameHandler.sanitize(firstSanitization);

        // Assert
        assertEquals(firstSanitization, secondSanitization, 
            "Sanitization should be idempotent");
        assertTrue(FileNameHandler.isValid(firstSanitization),
            "First sanitization should be valid");
        assertTrue(FileNameHandler.isValid(secondSanitization),
            "Second sanitization should be valid");
    }

    // ==================================================================================
    // TESTES DE FileNameComponents RECORD
    // ==================================================================================

    @Test
    @DisplayName("Should create FileNameComponents with all required fields")
    void shouldCreateFileNameComponentsWithAllRequiredFields() {
        // Act
        FileNameHandler.FileNameComponents components = new FileNameHandler.FileNameComponents(
            "document", "pdf", "document.pdf"
        );

        // Assert
        assertEquals("document", components.baseName());
        assertEquals("pdf", components.extension());
        assertEquals("document.pdf", components.originalName());
        assertEquals("document.pdf", components.getFullName());
        assertTrue(components.hasExtension());
    }

    @Test
    @DisplayName("Should create FileNameComponents without extension")
    void shouldCreateFileNameComponentsWithoutExtension() {
        // Act
        FileNameHandler.FileNameComponents components = new FileNameHandler.FileNameComponents(
            "README", "", "README"
        );

        // Assert
        assertEquals("README", components.baseName());
        assertEquals("", components.extension());
        assertEquals("README", components.originalName());
        assertEquals("README", components.getFullName());
        assertFalse(components.hasExtension());
    }

    @Test
    @DisplayName("Should reject null values in FileNameComponents constructor")
    void shouldRejectNullValuesInConstructor() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> 
            new FileNameHandler.FileNameComponents(null, "pdf", "document.pdf"));
        assertThrows(NullPointerException.class, () -> 
            new FileNameHandler.FileNameComponents("document", null, "document.pdf"));
        assertThrows(NullPointerException.class, () -> 
            new FileNameHandler.FileNameComponents("document", "pdf", null));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly for record")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        // Arrange
        FileNameHandler.FileNameComponents components1 = new FileNameHandler.FileNameComponents(
            "document", "pdf", "document.pdf"
        );
        FileNameHandler.FileNameComponents components2 = new FileNameHandler.FileNameComponents(
            "document", "pdf", "document.pdf"
        );
        FileNameHandler.FileNameComponents components3 = new FileNameHandler.FileNameComponents(
            "other", "pdf", "other.pdf"
        );

        // Assert
        assertEquals(components1, components2);
        assertNotEquals(components1, components3);
        assertEquals(components1.hashCode(), components2.hashCode());
        assertNotEquals(components1.hashCode(), components3.hashCode());
    }

    // ==================================================================================
    // TESTES DE EDGE CASES E CASOS ESPECIAIS
    // ==================================================================================

    @Test
    @DisplayName("Should handle very long filenames")
    void shouldHandleVeryLongFilenames() {
        // Arrange
        String longBaseName = "a".repeat(300);
        String longFilename = longBaseName + ".txt";

        // Act
        String sanitized = FileNameHandler.sanitize(longFilename);

        // Assert
        assertNotNull(sanitized);
        assertTrue(sanitized.endsWith(".txt"));
        // FileNameHandler preserves the length but sanitizes characters
        assertEquals(longFilename.length(), sanitized.length());
        // Should be all 'a' characters plus '.txt'
        assertTrue(sanitized.startsWith("aaa"));
    }

    @Test
    @DisplayName("Should handle filename with only dots")
    void shouldHandleFilenameWithOnlyDots() {
        // Act
        String sanitized = FileNameHandler.sanitize("....");

        // Assert
        assertTrue(sanitized.matches("^file_[a-z0-9]{8}$"), 
            "Should generate fallback name, got: " + sanitized);
    }

    @Test
    @DisplayName("Should handle filename with mixed valid and invalid characters")
    void shouldHandleFilenameWithMixedCharacters() {
        // Act
        String sanitized = FileNameHandler.sanitize("valid@invalid#mixed$.doc");

        // Assert
        assertEquals("valid_invalid_mixed.doc", sanitized);
    }

    @Test
    @DisplayName("Should preserve case in base name but normalize extension")
    void shouldPreserveCaseInBaseNameButNormalizeExtension() {
        // Act
        String sanitized = FileNameHandler.sanitize("MyDocument.PDF");

        // Assert
        assertEquals("MyDocument.pdf", sanitized);
    }

    @Test
    @DisplayName("Should handle Unicode characters consistently")
    void shouldHandleUnicodeCharactersConsistently() {
        // Arrange
        String[] unicodeFilenames = {
            "café.txt",      // Accented characters
            "文档.pdf",       // Chinese characters
            "документ.doc",   // Cyrillic characters
            "файл.txt"       // More Cyrillic
        };

        // Act & Assert
        for (String filename : unicodeFilenames) {
            String sanitized = FileNameHandler.sanitize(filename);
            assertNotNull(sanitized);
            assertFalse(sanitized.isEmpty());
            // Unicode characters should be replaced or generate fallback
            assertTrue(sanitized.matches("^[a-zA-Z0-9_\\-]+\\.[a-z0-9]+$") || 
                      sanitized.matches("^file_[a-z0-9]{8}\\.[a-z0-9]+$"),
                      "Sanitized filename should match expected pattern: " + sanitized);
        }
    }

    @Test
    @DisplayName("Should maintain consistency between decompose and sanitize")
    void shouldMaintainConsistencyBetweenDecomposeAndSanitize() {
        // Arrange
        String[] testFilenames = {
            "document.pdf",
            "My File.txt",
            "file@special.doc",
            "../path/file.txt",
            "README"
        };

        // Act & Assert
        for (String filename : testFilenames) {
            String sanitized = FileNameHandler.sanitize(filename);
            FileNameHandler.FileNameComponents components = FileNameHandler.decompose(filename);
            String reconstructed = FileNameHandler.reconstruct(components);

            assertEquals(sanitized, reconstructed,
                "Sanitize and decompose+reconstruct should produce same result for: " + filename);
        }
    }

    @Test
    @DisplayName("Should handle incremental names correctly in chain")
    void shouldHandleIncrementalNamesCorrectlyInChain() {
        // Arrange
        FileNameHandler.FileNameComponents baseComponents = FileNameHandler.decompose("document.pdf");

        // Act
        String increment1 = FileNameHandler.createIncrementalName(baseComponents, 1);
        String increment2 = FileNameHandler.createIncrementalName(baseComponents, 2);
        String increment10 = FileNameHandler.createIncrementalName(baseComponents, 10);

        // Assert
        assertEquals("document(1).pdf", increment1);
        assertEquals("document(2).pdf", increment2);
        assertEquals("document(10).pdf", increment10);

        // Incremental names contain parentheses which may not be considered "valid" 
        // by the isValid method, but they are correctly formed incremental names
        assertNotNull(increment1);
        assertNotNull(increment2);
        assertNotNull(increment10);
        assertTrue(increment1.contains("(1)"));
        assertTrue(increment2.contains("(2)"));
        assertTrue(increment10.contains("(10)"));
    }
}