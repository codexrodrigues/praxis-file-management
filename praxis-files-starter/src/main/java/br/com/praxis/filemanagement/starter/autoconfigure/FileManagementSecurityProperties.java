package br.com.praxis.filemanagement.starter.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Propriedades de configuração de segurança para a biblioteca de gerenciamento de arquivos
 * Permite customização flexível das configurações de segurança
 *
 * @author ErgonX
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "file.management.security")
@Validated
public class FileManagementSecurityProperties {

    /**
     * Define se os endpoints de arquivo devem ser permitidos sem autenticação
     */
    private boolean permitFileEndpoints = false;

    /**
     * Define se os endpoints do actuator devem ser permitidos sem autenticação
     */
    private boolean permitActuatorEndpoints = false;

    /**
     * Define se os endpoints de health/monitoramento devem ser permitidos sem autenticação
     */
    private boolean permitHealthEndpoints = true;

    /**
     * Define se a autenticação é requerida por padrão para todos os endpoints não configurados
     */
    private boolean requireAuthenticationByDefault = true;

    /**
     * Lista de padrões de URL que devem ser permitidos sem autenticação
     * Exemplo: ["/api/public/**", "/docs/**"]
     */
    private List<String> permittedPatterns = new ArrayList<>();

    /**
     * Lista de padrões de URL que sempre requerem autenticação
     * Tem precedência sobre permittedPatterns
     */
    private List<String> protectedPatterns = new ArrayList<>();

    /**
     * Define se o CSRF deve ser habilitado
     */
    private boolean csrfEnabled = false;

    /**
     * Define se o CORS deve ser configurado automaticamente
     */
    private boolean corsEnabled = true;

    /**
     * Lista de origens permitidas para CORS
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * Taxa máxima de requests por minuto por IP
     */
    private int maxRequestRatePerMinute = 100;

    /**
     * Define se a assinatura de requests deve ser habilitada
     */
    private boolean enableRequestSigning = false;

    /**
     * Define se uma API key é obrigatória
     */
    private boolean requireApiKey = false;

    // Getters e Setters

    public boolean isPermitFileEndpoints() {
        return permitFileEndpoints;
    }

    public void setPermitFileEndpoints(boolean permitFileEndpoints) {
        this.permitFileEndpoints = permitFileEndpoints;
    }

    public boolean isPermitActuatorEndpoints() {
        return permitActuatorEndpoints;
    }

    public void setPermitActuatorEndpoints(boolean permitActuatorEndpoints) {
        this.permitActuatorEndpoints = permitActuatorEndpoints;
    }

    public boolean isPermitHealthEndpoints() {
        return permitHealthEndpoints;
    }

    public void setPermitHealthEndpoints(boolean permitHealthEndpoints) {
        this.permitHealthEndpoints = permitHealthEndpoints;
    }

    public boolean isRequireAuthenticationByDefault() {
        return requireAuthenticationByDefault;
    }

    public void setRequireAuthenticationByDefault(boolean requireAuthenticationByDefault) {
        this.requireAuthenticationByDefault = requireAuthenticationByDefault;
    }

    public List<String> getPermittedPatterns() {
        return permittedPatterns;
    }

    public void setPermittedPatterns(List<String> permittedPatterns) {
        this.permittedPatterns = permittedPatterns;
    }

    public List<String> getProtectedPatterns() {
        return protectedPatterns;
    }

    public void setProtectedPatterns(List<String> protectedPatterns) {
        this.protectedPatterns = protectedPatterns;
    }

    public boolean isCsrfEnabled() {
        return csrfEnabled;
    }

    public void setCsrfEnabled(boolean csrfEnabled) {
        this.csrfEnabled = csrfEnabled;
    }

    public boolean isCorsEnabled() {
        return corsEnabled;
    }

    public void setCorsEnabled(boolean corsEnabled) {
        this.corsEnabled = corsEnabled;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public int getMaxRequestRatePerMinute() {
        return maxRequestRatePerMinute;
    }

    public void setMaxRequestRatePerMinute(int maxRequestRatePerMinute) {
        this.maxRequestRatePerMinute = maxRequestRatePerMinute;
    }

    public boolean isEnableRequestSigning() {
        return enableRequestSigning;
    }

    public void setEnableRequestSigning(boolean enableRequestSigning) {
        this.enableRequestSigning = enableRequestSigning;
    }

    public boolean isRequireApiKey() {
        return requireApiKey;
    }

    public void setRequireApiKey(boolean requireApiKey) {
        this.requireApiKey = requireApiKey;
    }

    /**
     * Valida as configurações de segurança
     */
    @PostConstruct
    public void validateConfiguration() {
        if (maxRequestRatePerMinute <= 0) {
            throw new IllegalArgumentException("Max request rate per minute must be positive, got: " + maxRequestRatePerMinute);
        }
        if (maxRequestRatePerMinute > 1000) {
            throw new IllegalArgumentException("Max request rate per minute is too high (max 1000), got: " + maxRequestRatePerMinute);
        }
        
        // CORS validation: empty origins list defaults to "*" in auto-configuration
        // This is handled automatically, so no validation needed here
        
        // Validar padrões
        if (permittedPatterns != null) {
            for (String pattern : permittedPatterns) {
                if (pattern == null || pattern.trim().isEmpty()) {
                    throw new IllegalArgumentException("Permitted patterns cannot contain null or empty strings");
                }
            }
        }
        
        if (protectedPatterns != null) {
            for (String pattern : protectedPatterns) {
                if (pattern == null || pattern.trim().isEmpty()) {
                    throw new IllegalArgumentException("Protected patterns cannot contain null or empty strings");
                }
            }
        }
        
        // Validar origins se especificadas
        if (allowedOrigins != null) {
            for (String origin : allowedOrigins) {
                if (origin == null || origin.trim().isEmpty()) {
                    throw new IllegalArgumentException("Allowed origins cannot contain null or empty strings");
                }
                if (!origin.startsWith("http") && !origin.equals("*")) {
                    throw new IllegalArgumentException("Allowed origins must be valid URLs or '*', got: " + origin);
                }
            }
        }
    }
}
