package br.com.praxis.filemanagement.web.monitoring;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.monitoring.SystemMonitor;
import br.com.praxis.filemanagement.web.error.ErrorResponse;
import br.com.praxis.filemanagement.web.response.ApiEnvelopeFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * REST controller para monitoramento do sistema de gerenciamento de arquivos.
 * 
 * <p>Fornece endpoints de monitoramento e verificação de saúde do sistema sem 
 * necessidade do Spring Boot Actuator. Inclui funcionalidades de:
 * <ul>
 *   <li>Verificação de saúde do sistema com componentes detalhados</li>
 *   <li>Métricas do sistema com filtragem de informações sensíveis</li>
 *   <li>Status dos componentes individuais</li>
 *   <li>Ping simples para verificação rápida</li>
 *   <li>Informações de versão e recursos disponíveis</li>
 * </ul>
 * 
 * <p>O controller é habilitado condicionalmente através da propriedade:
 * {@code file.management.monitoring.enabled=true} (padrão: true)
 * 
 * @author ErgonX
 * @since 1.0.0
 */
@RestController
@RequestMapping("/file-management/monitoring")
@Tag(name = "File Management Monitoring")
@SecurityRequirement(name = "basicAuth")
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnProperty(prefix = "file.management.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
@PreAuthorize("hasRole(@fileManagementProperties.monitoring.requiredRole)")
public class MonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);

    @Autowired
    private SystemMonitor systemMonitor;

    @Autowired
    private FileManagementProperties properties;

    /**
     * Endpoint para verificação completa da saúde do sistema.
     * 
     * <p>Retorna informações detalhadas sobre o status do sistema incluindo:
     * <ul>
     *   <li>Status geral (HEALTHY, WARNING, DEGRADED, CRITICAL)</li>
     *   <li>Status de componentes individuais (armazenamento, vírus scanner, etc.)</li>
     *   <li>Métricas do sistema (uso de memória, espaço em disco, etc.)</li>
     *   <li>Mensagens de erro ou avisos</li>
     * </ul>
     * 
     * <p>O código de resposta HTTP reflete o status do sistema:
     * <ul>
     *   <li>200 (OK) - Sistema saudável ou com avisos menores</li>
     *   <li>503 (Service Unavailable) - Sistema degradado ou crítico</li>
     *   <li>500 (Internal Server Error) - Erro interno ou status desconhecido</li>
     * </ul>
     * 
     * @param request Requisição HTTP para captura do IP do cliente (para logs)
     * @return ResponseEntity contendo SystemHealth com status detalhado do sistema
     * @since 1.0.0
     */
    @Operation(summary = "Retorna o status completo de saúde do sistema")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Sistema saudável ou com avisos",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SystemMonitor.SystemHealth.class),
                examples = @ExampleObject(
                    name = "Sistema saudável",
                    value = """
                        {
                          \"status\": \"HEALTHY\",
                          \"components\": { \"storage\": \"HEALTHY\", \"virusScanner\": \"DISABLED\" },
                          \"metrics\": { \"freeDiskSpaceBytes\": 104857600 }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Sistema degradado ou crítico",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SystemMonitor.SystemHealth.class),
                examples = @ExampleObject(
                    name = "Sistema degradado",
                    value = """
                        {
                          \"status\": \"DEGRADED\",
                          \"components\": { \"storage\": \"DEGRADED\" },
                          \"message\": \"Disk space low\"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro ao verificar saúde",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = SystemMonitor.SystemHealth.class),
                examples = @ExampleObject(
                    name = "Erro interno",
                    value = """
                        {
                          \"status\": \"CRITICAL\",
                          \"message\": \"Health check failed: timeout\"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Autenticação necessária",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"NAO_AUTORIZADO\",
                          \"status\": \"failure\",
                          \"code\": \"NAO_AUTORIZADO\",
                          \"message\": \"Autenticação necessária\",
                          \"errors\": [
                            {
                              \"code\": \"NAO_AUTORIZADO\",
                              \"message\": \"Autenticação necessária\"
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Acesso negado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"FORBIDDEN\",
                          \"status\": \"failure\",
                          \"code\": \"FORBIDDEN\",
                          \"message\": \"Acesso negado\",
                          \"errors\": [
                            {
                              \"code\": \"FORBIDDEN\",
                              \"message\": \"Acesso negado\"
                            }
                          ]
                        }
                        """
                )
            )
        )
    })
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SystemMonitor.SystemHealth> getHealth(HttpServletRequest request) {
        // Log access if configured
        if (properties.getMonitoring().isLogHealthCheckAccess()) {
            logger.info("Health check accessed from IP: {}", getClientIpAddress(request));
        }
        try {
            SystemMonitor.SystemHealth health = systemMonitor.getSystemHealth();

            // Set HTTP status based on system status
            switch (health.getStatus()) {
                case HEALTHY:
                case WARNING:
                    return ResponseEntity.ok(health);
                case DEGRADED:
                    return ResponseEntity.status(503).body(health); // Service Unavailable
                case CRITICAL:
                    return ResponseEntity.status(503).body(health); // Service Unavailable
                default:
                    return ResponseEntity.status(500).body(health); // Internal Server Error
            }

        } catch (Exception e) {
            SystemMonitor.SystemHealth errorHealth = new SystemMonitor.SystemHealth();
            errorHealth.setStatus(SystemMonitor.SystemStatus.CRITICAL);
            errorHealth.setMessage("Health check failed: " + e.getMessage());
            return ResponseEntity.status(500).body(errorHealth);
        }
    }

    /**
     * Endpoint para recuperação de métricas detalhadas do sistema.
     * 
     * <p>Retorna métricas operacionais do sistema com filtragem automática de informações
     * sensíveis baseada na configuração {@code includeDetailedMetrics}:
     * <ul>
     *   <li><strong>Métricas básicas</strong>: Status dos componentes, contadores de upload</li>
     *   <li><strong>Métricas detalhadas</strong>: Configurações, caminhos, hosts (se habilitado)</li>
     *   <li><strong>Métricas de segurança</strong>: Resumos de falhas, tentativas bloqueadas</li>
     * </ul>
     * 
     * <p>A filtragem remove automaticamente informações sensíveis como:
     * <ul>
     *   <li>Caminhos de diretórios do sistema</li>
     *   <li>Configurações de hosts e conexões</li>
     *   <li>Detalhes de configuração interna</li>
     * </ul>
     * 
     * @param request Requisição HTTP para captura do IP do cliente (para logs)
     * @return ResponseEntity contendo métricas filtradas do sistema ou erro 500
     * @since 1.0.0
     */
    @Operation(summary = "Recupera métricas detalhadas do sistema")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Métricas retornadas",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Métricas básicas",
                    value = """
                        {
                          \"uploads.total\": 42,
                          \"uploads.failed\": 1
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro ao obter métricas",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Falha ao obter",
                    value = """
                        { \"error\": \"Failed to retrieve metrics: connection refused\" }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Autenticação necessária",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"NAO_AUTORIZADO\",
                          \"status\": \"failure\",
                          \"code\": \"NAO_AUTORIZADO\",
                          \"message\": \"Autenticação necessária\",
                          \"errors\": [
                            {
                              \"code\": \"NAO_AUTORIZADO\",
                              \"message\": \"Autenticação necessária\"
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Acesso negado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"FORBIDDEN\",
                          \"status\": \"failure\",
                          \"code\": \"FORBIDDEN\",
                          \"message\": \"Acesso negado\",
                          \"errors\": [
                            {
                              \"code\": \"FORBIDDEN\",
                              \"message\": \"Acesso negado\"
                            }
                          ]
                        }
                        """
                )
            )
        )
    })
    @GetMapping(value = "/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getMetrics(HttpServletRequest request) {
        // Log access if configured
        if (properties.getMonitoring().isLogHealthCheckAccess()) {
            logger.info("Metrics accessed from IP: {}", getClientIpAddress(request));
        }

        try {
            SystemMonitor.SystemHealth health = systemMonitor.getSystemHealth();
            Object metrics = filterSensitiveMetrics(health.getMetrics());
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Failed to retrieve metrics: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Endpoint para verificação específica do status dos componentes do sistema.
     * 
     * <p>Retorna apenas o status dos componentes individuais sem métricas adicionais:
     * <ul>
     *   <li><strong>Storage</strong>: Status do sistema de armazenamento local</li>
     *   <li><strong>VirusScanner</strong>: Status do scanner de vírus (se habilitado)</li>
     *   <li><strong>RateLimiter</strong>: Status do limitador de taxa</li>
     *   <li><strong>SecurityAudit</strong>: Status do sistema de auditoria</li>
     * </ul>
     * 
     * <p>Útil para verificações rápidas de componentes específicos sem overhead
     * de métricas completas do sistema.
     * 
     * @return ResponseEntity contendo mapa de componentes e seus status ou erro 500
     * @since 1.0.0
     */
    @Operation(summary = "Consulta status dos componentes do sistema")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Status retornado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Status dos componentes",
                    value = """
                        {
                          \"storage\": \"HEALTHY\",
                          \"virusScanner\": \"DISABLED\"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro ao obter status",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Falha ao obter",
                    value = """
                        { \"error\": \"Failed to retrieve status: timeout\" }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Autenticação necessária",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"NAO_AUTORIZADO\",
                          \"status\": \"failure\",
                          \"code\": \"NAO_AUTORIZADO\",
                          \"message\": \"Autenticação necessária\",
                          \"errors\": [
                            {
                              \"code\": \"NAO_AUTORIZADO\",
                              \"message\": \"Autenticação necessária\"
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Acesso negado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"FORBIDDEN\",
                          \"status\": \"failure\",
                          \"code\": \"FORBIDDEN\",
                          \"message\": \"Acesso negado\",
                          \"errors\": [
                            {
                              \"code\": \"FORBIDDEN\",
                              \"message\": \"Acesso negado\"
                            }
                          ]
                        }
                        """
                )
            )
        )
    })
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getStatus() {
        try {
            SystemMonitor.SystemHealth health = systemMonitor.getSystemHealth();
            return ResponseEntity.ok(health.getComponents());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"Failed to retrieve status: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Endpoint de ping simples para verificação rápida de saúde do sistema.
     * 
     * <p>Retorna resposta de texto simples indicando o status básico do sistema.
     * Ideal para verificações automatizadas de disponibilidade (health checks de load balancer).
     * 
     * <p>Respostas possíveis:
     * <ul>
     *   <li><strong>"HEALTHY"</strong> (200) - Sistema operacional normal</li>
     *   <li><strong>"DEGRADED"</strong> (503) - Sistema com problemas mas funcional</li>
     *   <li><strong>"CRITICAL"</strong> (503) - Sistema com problemas críticos</li>
     *   <li><strong>"ERROR"</strong> (500) - Erro na verificação de saúde</li>
     *   <li><strong>"UNKNOWN"</strong> (500) - Status não reconhecido</li>
     * </ul>
     * 
     * @return ResponseEntity com texto simples indicando status e código HTTP apropriado
     * @since 1.0.0
     */
    @Operation(summary = "Verificação simples de disponibilidade")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Sistema operacional",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "HEALTHY")
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Sistema degradado ou crítico",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "DEGRADED")
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro ao verificar disponibilidade",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "ERROR")
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Autenticação necessária",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"NAO_AUTORIZADO\",
                          \"status\": \"failure\",
                          \"code\": \"NAO_AUTORIZADO\",
                          \"message\": \"Autenticação necessária\",
                          \"errors\": [
                            {
                              \"code\": \"NAO_AUTORIZADO\",
                              \"message\": \"Autenticação necessária\"
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Acesso negado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"error\": \"FORBIDDEN\",
                          \"status\": \"failure\",
                          \"code\": \"FORBIDDEN\",
                          \"message\": \"Acesso negado\",
                          \"errors\": [
                            {
                              \"code\": \"FORBIDDEN\",
                              \"message\": \"Acesso negado\"
                            }
                          ]
                        }
                        """
                )
            )
        )
    })
    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ping() {
        try {
            SystemMonitor.SystemHealth health = systemMonitor.getSystemHealth();

            switch (health.getStatus()) {
                case HEALTHY:
                case WARNING:
                    return ResponseEntity.ok("HEALTHY");
                case DEGRADED:
                    return ResponseEntity.status(503).body("DEGRADED");
                case CRITICAL:
                    return ResponseEntity.status(503).body("CRITICAL");
                default:
                    return ResponseEntity.status(500).body("UNKNOWN");
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body("ERROR");
        }
    }

    /**
     * Extrai o endereço IP real do cliente da requisição HTTP.
     * 
     * <p>Verifica múltiplos headers em ordem de precedência para obter o IP correto
     * mesmo quando a requisição passa por proxies ou load balancers:
     * <ol>
     *   <li><strong>X-Forwarded-For</strong>: IP original (primeiro da lista se múltiplos)</li>
     *   <li><strong>X-Real-IP</strong>: IP real definido pelo proxy</li>
     *   <li><strong>Remote Address</strong>: IP direto da conexão</li>
     * </ol>
     * 
     * @param request Requisição HTTP contendo headers de IP
     * @return String com endereço IP do cliente ou IP da conexão direta
     * @since 1.0.0
     */
    private String getClientIpAddress(HttpServletRequest request) {
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
     * Endpoint para informações de versão, build e recursos disponíveis do sistema.
     * 
     * <p>Retorna informações detalhadas sobre a versão atual da aplicação:
     * <ul>
     *   <li><strong>version</strong>: Versão da aplicação</li>
     *   <li><strong>buildTimestamp</strong>: Data/hora atual (timestamp de consulta)</li>
     *   <li><strong>codeVersion</strong>: Identificador específico da versão do código</li>
     *   <li><strong>features</strong>: Mapa de recursos habilitados/desabilitados</li>
     *   <li><strong>debug</strong>: Informações de debug e assinatura de desenvolvimento</li>
     * </ul>
     * 
     * <p>As informações de recursos refletem configurações atuais:
     * <ul>
     *   <li>{@code failFastMode}: Se modo fail-fast está disponível</li>
     *   <li>{@code bulkUpload}: Se upload múltiplo está disponível</li>
     *   <li>{@code virusScanning}: Se scanner de vírus está habilitado</li>
     *   <li>{@code strictValidation}: Se validação rigorosa está habilitada</li>
     * </ul>
     * 
     * @return ResponseEntity contendo mapa com informações de versão e recursos
     * @since 1.0.0
     */
    @Operation(summary = "Retorna informações de versão e recursos disponíveis")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Informações de versão retornadas",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Versão e recursos",
                    value = """
                        {
                          \"status\": \"success\",
                          \"message\": \"Informações de versão recuperadas com sucesso\",
                          \"timestamp\": \"2025-07-21T20:15:35.456Z\",
                          \"data\": {
                            \"version\": \"1.1.0-SNAPSHOT\",
                            \"buildTimestamp\": \"2025-07-21T20:15:35.456Z\",
                            \"codeVersion\": \"v2.1-failfast-enabled\",
                            \"features\": {
                              \"failFastMode\": true,
                              \"bulkUpload\": true,
                              \"virusScanning\": false,
                              \"strictValidation\": true
                            }
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro ao obter informações de versão",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Falha ao obter",
                    value = """
                        {
                          \"status\": \"failure\",
                          \"message\": \"Erro interno durante o processamento\",
                          \"errors\": [
                            {
                              \"code\": \"ERRO_INTERNO\",
                              \"message\": \"Erro interno durante o processamento\"
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Autenticação necessária",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"status\": \"failure\",
                          \"message\": \"Autenticação necessária\",
                          \"errors\": [
                            {
                              \"code\": \"NAO_AUTORIZADO\",
                              \"message\": \"Autenticação necessária\"
                            }
                          ]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Acesso negado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          \"status\": \"failure\",
                          \"message\": \"Acesso negado\",
                          \"errors\": [
                            {
                              \"code\": \"FORBIDDEN\",
                              \"message\": \"Acesso negado\"
                            }
                          ]
                        }
                        """
                )
            )
        )
    })
    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getVersion() {
        Map<String, Object> versionInfo = Map.of(
            "version", "1.1.0-SNAPSHOT",
            "buildTimestamp", java.time.Instant.now().toString(),
            "codeVersion", "v2.1-failfast-enabled",
            "features", Map.of(
                "failFastMode", true,
                "bulkUpload", true,
                "virusScanning", properties.getVirusScanning().isEnabled(),
                "strictValidation", properties.getSecurity().isStrictValidation()
            )
        );

        logger.info("Version endpoint accessed - code version: {}", versionInfo.get("codeVersion"));
        return ResponseEntity.ok(
            ApiEnvelopeFactory.success(versionInfo, "Informações de versão recuperadas com sucesso")
        );
    }

    /**
     * Filtra informações sensíveis das métricas baseado na configuração do sistema.
     * 
     * <p>Remove automaticamente dados sensíveis das métricas quando
     * {@code includeDetailedMetrics} está desabilitado:
     * 
     * <p><strong>Informações mantidas (seguras para exposição):</strong>
     * <ul>
     *   <li>Métricas de configuração básicas (sem diretórios ou hosts)</li>
     *   <li>Resumos de segurança (sem detalhes específicos)</li>
     *   <li>Contadores e estatísticas gerais</li>
     * </ul>
     * 
     * <p><strong>Informações filtradas (sensíveis):</strong>
     * <ul>
     *   <li>Caminhos de diretórios do sistema</li>
     *   <li>Configurações de hosts e conexões</li>
     *   <li>Detalhes específicos de configuração interna</li>
     * </ul>
     * 
     * @param metrics Objeto de métricas original (pode ser Map ou outro tipo)
     * @return Objeto de métricas filtrado ou original se filtragem não aplicável
     * @since 1.0.0
     */
    private Object filterSensitiveMetrics(Object metrics) {
        if (!properties.getMonitoring().isIncludeDetailedMetrics()) {
            if (metrics instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metricsMap = (Map<String, Object>) metrics;
                Map<String, Object> filtered = new java.util.HashMap<>();

                // Only include basic status metrics, exclude sensitive paths and configurations
                for (Map.Entry<String, Object> entry : metricsMap.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("configuration.") && !key.contains("Dir") && !key.contains("Host")) {
                        filtered.put(key, entry.getValue());
                    } else if (key.startsWith("security.") && !key.contains("summary")) {
                        filtered.put(key, entry.getValue());
                    }
                }
                return filtered;
            }
        }
        return metrics;
    }
}
