package br.com.praxis.filemanagement.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

/**
 * Validator for enforcing critical security configurations in production environment.
 * 
 * This component ensures that security-critical settings are properly configured
 * when running in production profile, preventing common security misconfigurations.
 */
@Component
@Profile("production")
public class ProductionSecurityValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductionSecurityValidator.class);
    
    @Autowired
    private FileManagementProperties fileManagementProperties;
    
    @Autowired
    private Environment environment;
    
    /**
     * Validates security configurations at application startup in production.
     * 
     * Enforces the following security requirements:
     * - strictValidation must be enabled 
     * - virus scanning should be enabled
     * - mandatory virus scan is recommended
     * - suspicious extension blocking should be enabled
     * - magic number validation should be enabled
     * 
     * @throws IllegalStateException if critical security settings are disabled
     */
    @PostConstruct
    public void validateProductionSecuritySettings() {
        logger.info("🔒 Validating production security configurations...");
        
        String[] activeProfiles = environment.getActiveProfiles();
        logger.info("Active profiles: {}", Arrays.toString(activeProfiles));
        
        // First validate that we're actually in production
        validateProductionProfile();
        
        // Then validate security settings
        validateCriticalSecuritySettings();
        validateRecommendedSecuritySettings();
        
        logger.info("✅ Production security validation completed successfully");
    }
    
    /**
     * Validates settings that are REQUIRED in production.
     * Application startup will fail if these are not properly configured.
     */
    private void validateCriticalSecuritySettings() {
        // Critical: strictValidation must be enabled in production
        if (fileManagementProperties.getSecurity() == null || 
            !fileManagementProperties.getSecurity().isStrictValidation()) {
            throw new IllegalStateException(
                "🚨 CRITICAL SECURITY ERROR: strictValidation must be enabled in production environment. " +
                "Set file-management.security.strict-validation=true in your production configuration."
            );
        }
        logger.info("✅ Strict validation: ENABLED");
        
        // Critical: Suspicious extensions blocking should be enabled
        if (fileManagementProperties.getSecurity() == null || 
            !fileManagementProperties.getSecurity().isBlockSuspiciousExtensions()) {
            throw new IllegalStateException(
                "🚨 CRITICAL SECURITY ERROR: blockSuspiciousExtensions must be enabled in production environment. " +
                "Set file-management.security.block-suspicious-extensions=true in your production configuration."
            );
        }
        logger.info("✅ Suspicious extensions blocking: ENABLED");
    }
    
    /**
     * Validates settings that are RECOMMENDED in production.
     * Logs warnings but does not fail application startup.
     */
    private void validateRecommendedSecuritySettings() {
        // Recommended: Virus scanning should be enabled
        if (fileManagementProperties.getVirusScanning() == null || 
            !fileManagementProperties.getVirusScanning().isEnabled()) {
            logger.warn("⚠️ SECURITY WARNING: Virus scanning is disabled in production. " +
                       "Consider enabling file-management.virus-scanning.enabled=true");
        } else {
            logger.info("✅ Virus scanning: ENABLED");
        }
        
        // Recommended: Mandatory virus scan in production
        if (fileManagementProperties.getSecurity() == null || 
            !fileManagementProperties.getSecurity().isMandatoryVirusScan()) {
            logger.warn("⚠️ SECURITY WARNING: Mandatory virus scan is disabled in production. " +
                       "Consider enabling file-management.security.mandatory-virus-scan=true");
        } else {
            logger.info("✅ Mandatory virus scan: ENABLED");
        }
        
        // Recommended: Magic number validation should be enabled  
        if (fileManagementProperties.getSecurity() == null || 
            !fileManagementProperties.getSecurity().isMagicNumberValidation()) {
            logger.warn("⚠️ SECURITY WARNING: Magic number validation is disabled in production. " +
                       "Consider enabling file-management.security.magic-number-validation=true");
        } else {
            logger.info("✅ Magic number validation: ENABLED");
        }
        
        // Recommended: Deep content inspection when using mandatory virus scan
        if (fileManagementProperties.getSecurity() != null && 
            fileManagementProperties.getSecurity().isMandatoryVirusScan() &&
            !fileManagementProperties.getSecurity().isDeepContentInspection()) {
            logger.warn("⚠️ CONFIGURATION WARNING: Mandatory virus scan is enabled but deep content inspection is disabled. " +
                       "This may cause configuration validation failures.");
        }
    }
    
    /**
     * Validates that the current environment is actually production.
     * Useful for debugging profile issues.
     */
    public void validateProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean hasProductionProfile = Arrays.asList(activeProfiles).contains("production");
        
        if (!hasProductionProfile) {
            logger.warn("⚠️ ProductionSecurityValidator is active but 'production' profile not found in active profiles: {}. " +
                       "This validator should only run in production environment.", Arrays.toString(activeProfiles));
        }
    }
}