package br.com.praxis.filemanagement.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste básico para verificar se o framework de testes funciona
 */
@DisplayName("Basic Test")
class BasicTest {
    
    @Test
    @DisplayName("Should pass basic test")
    void shouldPassBasicTest() {
        assertTrue(true, "Basic test should always pass");
    }
    
    @Test
    @DisplayName("Should verify enum values exist")
    void shouldVerifyEnumValuesExist() {
        // Test if the enum values we need actually exist
        assertNotNull(br.com.praxis.filemanagement.api.enums.FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED);
        assertNotNull(br.com.praxis.filemanagement.api.enums.FileErrorReason.VIRUS_SCAN_UNAVAILABLE);
        assertNotNull(br.com.praxis.filemanagement.api.enums.FileErrorReason.MAGIC_NUMBER_MISMATCH);
    }
    
    @Test
    @DisplayName("Should verify FileUploadOptionsRecord works")
    void shouldVerifyFileUploadOptionsRecord() {
        var options = br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord.defaultOptions();
        assertNotNull(options);
        assertTrue(options.maxUploadSizeMb() > 0);
    }
}