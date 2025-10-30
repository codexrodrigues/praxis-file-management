package br.com.praxis.filemanagement.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes para o ProductionSecurityValidator
 * Verifica as validações de segurança obrigatórias em produção
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Production Security Validator Tests")
class ProductionSecurityValidatorTest {

    @Mock
    private Environment environment;
    
    private ProductionSecurityValidator validator;
    private FileManagementProperties properties;
    
    @BeforeEach
    void setUp() {
        validator = new ProductionSecurityValidator();
        properties = new FileManagementProperties();
        
        // Use reflection to inject dependencies
        try {
            java.lang.reflect.Field propertiesField = ProductionSecurityValidator.class.getDeclaredField("fileManagementProperties");
            propertiesField.setAccessible(true);
            propertiesField.set(validator, properties);
            
            java.lang.reflect.Field envField = ProductionSecurityValidator.class.getDeclaredField("environment");
            envField.setAccessible(true);
            envField.set(validator, environment);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test dependencies", e);
        }
        
        // Default setup - production profile
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});
    }
    
    // ==================================================================================
    // TESTES DE CONFIGURAÇÕES CRÍTICAS (OBRIGATÓRIAS)
    // ==================================================================================
    
    @Test
    @DisplayName("Should pass validation when all critical settings are enabled")
    void shouldPassValidationWhenAllCriticalSettingsEnabled() {
        // Arrange
        setupValidProductionConfig();
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> validator.validateProductionSecuritySettings());
    }
    
    @Test
    @DisplayName("Should fail when strictValidation is disabled in production")
    void shouldFailWhenStrictValidationDisabled() {
        // Arrange
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setStrictValidation(false); // ❌ CRITICAL ERROR
        security.setBlockSuspiciousExtensions(true);
        properties.setSecurity(security);
        
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> validator.validateProductionSecuritySettings());
        
        assertTrue(exception.getMessage().contains("strictValidation must be enabled"));
        assertTrue(exception.getMessage().contains("CRITICAL SECURITY ERROR"));
    }
    
    @Test
    @DisplayName("Should fail when blockSuspiciousExtensions is disabled in production")
    void shouldFailWhenBlockSuspiciousExtensionsDisabled() {
        // Arrange
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setStrictValidation(true);
        security.setBlockSuspiciousExtensions(false); // ❌ CRITICAL ERROR
        properties.setSecurity(security);
        
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> validator.validateProductionSecuritySettings());
        
        assertTrue(exception.getMessage().contains("blockSuspiciousExtensions must be enabled"));
        assertTrue(exception.getMessage().contains("CRITICAL SECURITY ERROR"));
    }
    
    @Test
    @DisplayName("Should fail when security configuration is null")
    void shouldFailWhenSecurityConfigurationIsNull() {
        // Arrange
        properties.setSecurity(null); // ❌ NULL SECURITY CONFIG
        
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> validator.validateProductionSecuritySettings());
        
        assertTrue(exception.getMessage().contains("strictValidation must be enabled"));
    }
    
    // ==================================================================================
    // TESTES DE CONFIGURAÇÕES RECOMENDADAS (WARNINGS)
    // ==================================================================================
    
    @Test
    @DisplayName("Should pass but log warnings when recommended settings are disabled")
    void shouldPassButLogWarningsWhenRecommendedSettingsDisabled() {
        // Arrange
        setupMinimalValidProductionConfig();
        
        // Disable recommended settings
        properties.getVirusScanning().setEnabled(false);
        properties.getSecurity().setMandatoryVirusScan(false);
        properties.getSecurity().setMagicNumberValidation(false);
        
        // Act & Assert - should not throw exception but will log warnings
        assertDoesNotThrow(() -> validator.validateProductionSecuritySettings());
    }
    
    @Test
    @DisplayName("Should validate successfully with all recommended settings enabled")
    void shouldValidateSuccessfullyWithAllRecommendedSettingsEnabled() {
        // Arrange
        setupCompleteProductionConfig();
        
        // Act & Assert
        assertDoesNotThrow(() -> validator.validateProductionSecuritySettings());
    }
    
    // ==================================================================================
    // TESTES DE PROFILE VALIDATION
    // ==================================================================================
    
    @Test
    @DisplayName("Should handle missing production profile gracefully")
    void shouldHandleMissingProductionProfileGracefully() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"development", "test"});
        setupValidProductionConfig();
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> validator.validateProductionProfile());
    }
    
    @Test
    @DisplayName("Should validate production profile correctly")
    void shouldValidateProductionProfileCorrectly() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production", "cloud"});
        
        // Act & Assert
        assertDoesNotThrow(() -> validator.validateProductionProfile());
    }
    
    // ==================================================================================
    // TESTES DE EDGE CASES
    // ==================================================================================
    
    @Test
    @DisplayName("Should handle empty active profiles array")
    void shouldHandleEmptyActiveProfilesArray() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        setupValidProductionConfig();
        
        // Act & Assert
        assertDoesNotThrow(() -> validator.validateProductionProfile());
    }
    
    @Test
    @DisplayName("Should handle null environment getActiveProfiles")
    void shouldHandleNullEnvironmentGetActiveProfiles() {
        // Arrange
        when(environment.getActiveProfiles()).thenReturn(null);
        setupValidProductionConfig();
        
        // Act & Assert - should handle gracefully
        assertThrows(NullPointerException.class, () -> validator.validateProductionProfile());
    }
    
    // ==================================================================================
    // HELPER METHODS
    // ==================================================================================
    
    private void setupValidProductionConfig() {
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setStrictValidation(true);
        security.setBlockSuspiciousExtensions(true);
        security.setMagicNumberValidation(true);
        security.setMandatoryVirusScan(true);
        properties.setSecurity(security);
        
        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(true);
        properties.setVirusScanning(virusScanning);
    }
    
    private void setupMinimalValidProductionConfig() {
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setStrictValidation(true); // ✅ CRITICAL
        security.setBlockSuspiciousExtensions(true); // ✅ CRITICAL
        properties.setSecurity(security);
        
        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        properties.setVirusScanning(virusScanning);
    }
    
    private void setupCompleteProductionConfig() {
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setStrictValidation(true);
        security.setBlockSuspiciousExtensions(true);
        security.setMagicNumberValidation(true);
        security.setMandatoryVirusScan(true);
        security.setDeepContentInspection(true);
        properties.setSecurity(security);
        
        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(true);
        properties.setVirusScanning(virusScanning);
        
        FileManagementProperties.AuditLogging auditLogging = new FileManagementProperties.AuditLogging();
        auditLogging.setEnabled(true);
        properties.setAuditLogging(auditLogging);
    }
}