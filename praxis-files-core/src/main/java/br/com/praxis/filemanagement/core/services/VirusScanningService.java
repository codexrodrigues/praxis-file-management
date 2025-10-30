package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.FileUploadMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.ClamavException;
import xyz.capybara.clamav.commands.scan.result.ScanResult;
import xyz.capybara.clamav.Platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;

/**
 * Service for virus scanning using ClamAV.
 * Only available when ClamAV client is on the classpath and virus scanning is enabled.
 */
@Service
@ConditionalOnClass(ClamavClient.class)
@ConditionalOnProperty(prefix = "file.management.virus-scanning", name = "enabled", havingValue = "true")
public class VirusScanningService {

    private static final Logger logger = LoggerFactory.getLogger(VirusScanningService.class);

    @Autowired
    private FileManagementProperties properties;

    @Autowired(required = false)
    private FileUploadMetricsService metricsService;

    private volatile ClamavClient clamavClient;
    private volatile boolean clientInitialized = false;

    /**
     * Result of a virus scan operation
     */
    public static class ScanResult {
        private final boolean clean;
        private final String virusName;
        private final String errorMessage;

        private ScanResult(boolean clean, String virusName, String errorMessage) {
            this.clean = clean;
            this.virusName = virusName;
            this.errorMessage = errorMessage;
        }

        public static ScanResult clean() {
            return new ScanResult(true, null, null);
        }

        public static ScanResult infected(String virusName) {
            return new ScanResult(false, virusName, null);
        }

        public static ScanResult error(String errorMessage) {
            return new ScanResult(false, null, errorMessage);
        }

        public boolean isClean() {
            return clean;
        }

        public Optional<String> getVirusName() {
            return Optional.ofNullable(virusName);
        }

        public Optional<String> getErrorMessage() {
            return Optional.ofNullable(errorMessage);
        }

        public boolean isError() {
            return errorMessage != null;
        }
    }

    /**
     * Realiza escaneamento de vírus em bytes de arquivo usando ClamAV.
     * 
     * <p>Este método executa uma análise completa de segurança usando o servidor ClamAV configurado:
     * <ul>
     *   <li><strong>Validação de Entrada</strong>: Verifica se o arquivo não está vazio</li>
     *   <li><strong>Limite de Tamanho</strong>: Respeita limite máximo configurado (padrão: 50MB)</li>
     *   <li><strong>Conexão Lazy</strong>: Inicializa cliente ClamAV sob demanda com pool de conexões</li>
     *   <li><strong>Timeout Control</strong>: Aplica timeout configurável para evitar travamentos</li>
     *   <li><strong>Error Handling</strong>: Trata falhas de rede e indisponibilidade do scanner</li>
     * </ul>
     *
     * <p><strong>Comportamento Conforme Configuração:</strong>
     * <ul>
     *   <li><strong>Escaneamento Desabilitado</strong>: Retorna imediatamente como "clean"</li>
     *   <li><strong>Scanner Indisponível + failOnScannerUnavailable=true</strong>: Retorna erro</li>
     *   <li><strong>Scanner Indisponível + failOnScannerUnavailable=false</strong>: Permite upload (assume "clean")</li>
     * </ul>
     *
     * <p><strong>Segurança e Performance:</strong>
     * <ul>
     *   <li>Utiliza ExecutorService para timeout control</li>
     *   <li>Cleanup automático de recursos (threads, streams)</li>
     *   <li>Thread-safe com inicialização sincronizada do cliente</li>
     *   <li>Logging detalhado para auditoria de segurança</li>
     * </ul>
     *
     * @param fileBytes Array de bytes do arquivo a ser escaneado (não pode ser null ou vazio)
     * @param filename Nome do arquivo para logging e auditoria (usado apenas para logs)
     * @return ScanResult contendo:
     *         <ul>
     *           <li><strong>Clean</strong>: isClean()=true, sem vírus detectado</li>
     *           <li><strong>Infected</strong>: isClean()=false, getVirusName() contém nome do vírus</li>
     *           <li><strong>Error</strong>: isError()=true, getErrorMessage() contém detalhes do erro</li>
     *         </ul>
     * @throws IllegalArgumentException Se fileBytes for null (encapsulado no ScanResult.error)
     * @since 1.0.0
     * @see ScanResult
     * @see FileManagementProperties.VirusScanning
     */
    public ScanResult scanFile(byte[] fileBytes, String filename) {
        if (!properties.getVirusScanning().isEnabled()) {
            logger.debug("Virus scanning is disabled, skipping scan for: {}", filename);
            return ScanResult.clean();
        }

        if (fileBytes == null || fileBytes.length == 0) {
            logger.warn("Cannot scan empty file: {}", filename);
            return ScanResult.error("Cannot scan empty file");
        }

        // Check file size limit
        if (fileBytes.length > properties.getVirusScanning().getMaxFileSizeBytes()) {
            logger.warn("File too large for virus scanning: {} bytes, max: {} bytes",
                fileBytes.length, properties.getVirusScanning().getMaxFileSizeBytes());
            return ScanResult.error("File too large for virus scanning");
        }

        try {
            ClamavClient client = getOrCreateClient();
            if (client == null) {
                String errorMsg = "ClamAV client not available";
                logger.error("Security: {}", errorMsg);

                if (properties.getVirusScanning().isFailOnScannerUnavailable()) {
                    if (metricsService != null) {
                        metricsService.recordSecurityRejection("av_unavailable");
                    }
                    return ScanResult.error(errorMsg);
                } else {
                    logger.warn("Security: ClamAV unavailable but allowing upload (failOnScannerUnavailable=false)");
                    if (metricsService != null) {
                        metricsService.recordSecurityRejection("av_unavailable_fallback");
                    }
                    return ScanResult.clean();
                }
            }

            // Perform virus scan with read timeout
            return performScanWithTimeout(client, fileBytes, filename);
        } catch (ClamavException e) {
            String errorMsg = "ClamAV connection error: " + e.getMessage();
            logger.error("Security: {}", errorMsg, e);

            if (properties.getVirusScanning().isFailOnScannerUnavailable()) {
                if (metricsService != null) {
                    metricsService.recordSecurityRejection("av_unavailable");
                }
                return ScanResult.error(errorMsg);
            } else {
                logger.warn("Security: ClamAV connection error but allowing upload (failOnScannerUnavailable=false)");
                if (metricsService != null) {
                    metricsService.recordSecurityRejection("av_unavailable_fallback");
                }
                return ScanResult.clean();
            }
        }
    }

    /**
     * Perform virus scan with read timeout using ExecutorService
     */
    private ScanResult performScanWithTimeout(ClamavClient client, byte[] fileBytes, String filename) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ScanResult> future = executor.submit(() -> {
                try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                    xyz.capybara.clamav.commands.scan.result.ScanResult clamavResult = client.scan(inputStream);

                    if (clamavResult instanceof xyz.capybara.clamav.commands.scan.result.ScanResult.OK) {
                        logger.debug("Security: File clean - no virus detected: {}", filename);
                        return ScanResult.clean();
                    } else if (clamavResult instanceof xyz.capybara.clamav.commands.scan.result.ScanResult.VirusFound) {
                        xyz.capybara.clamav.commands.scan.result.ScanResult.VirusFound virusFound =
                            (xyz.capybara.clamav.commands.scan.result.ScanResult.VirusFound) clamavResult;
                        Map<String, Collection<String>> foundViruses = virusFound.getFoundViruses();

                        // Safe extraction of virus name with null checks
                        String virusName = "Unknown virus";
                        if (foundViruses != null && !foundViruses.isEmpty()) {
                            for (Collection<String> virusCollection : foundViruses.values()) {
                                if (virusCollection != null && !virusCollection.isEmpty()) {
                                    virusName = virusCollection.iterator().next();
                                    break;
                                }
                            }
                        }

                        if (properties.getVirusScanning().isQuarantineEnabled()) {
                            quarantineFile(fileBytes, filename);
                        }

                        logger.warn("Security: Virus detected in file: {} - Virus: {}", filename, virusName);
                        return ScanResult.infected(virusName);
                    } else {
                        String unknownError = "Unknown scan result type: " + clamavResult.getClass().getSimpleName();
                        logger.error("Security: {}", unknownError);
                        return ScanResult.error(unknownError);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Scan failed", e);
                }
            });

            try {
                return future.get(properties.getVirusScanning().getReadTimeout(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                String errorMsg = "Virus scan timeout after " + properties.getVirusScanning().getReadTimeout() + "ms";
                logger.error("Security: {}", errorMsg);

                if (properties.getVirusScanning().isFailOnScannerUnavailable()) {
                    if (metricsService != null) {
                        metricsService.recordSecurityRejection("av_unavailable");
                    }
                    return ScanResult.error(errorMsg);
                } else {
                    logger.warn("Security: Scan timeout but allowing upload (failOnScannerUnavailable=false)");
                    if (metricsService != null) {
                        metricsService.recordSecurityRejection("av_unavailable_fallback");
                    }
                    return ScanResult.clean();
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ClamavException) {
                    String errorMsg = "ClamAV scan error: " + cause.getMessage();
                    logger.error("Security: {}", errorMsg, cause);

                    if (properties.getVirusScanning().isFailOnScannerUnavailable()) {
                        if (metricsService != null) {
                            metricsService.recordSecurityRejection("av_unavailable");
                        }
                        return ScanResult.error(errorMsg);
                    } else {
                        logger.warn("Security: ClamAV scan error but allowing upload (failOnScannerUnavailable=false)");
                        if (metricsService != null) {
                            metricsService.recordSecurityRejection("av_unavailable_fallback");
                        }
                        return ScanResult.clean();
                    }
                } else {
                    String errorMsg = "Unexpected error during virus scan: " + cause.getMessage();
                    logger.error("Security: {}", errorMsg, cause);
                    return ScanResult.error(errorMsg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String errorMsg = "Virus scan interrupted";
                logger.error("Security: {}", errorMsg, e);
                return ScanResult.error(errorMsg);
            }
        } finally {
            executor.shutdown();
        }
    }

    private void quarantineFile(byte[] fileBytes, String originalFilename) {
        try {
            FileManagementProperties.VirusScanning cfg = properties.getVirusScanning();
            Path dir = Paths.get(cfg.getQuarantineDir());
            Files.createDirectories(dir);
            String sanitized = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String timestamped = System.currentTimeMillis() + "-" + sanitized;
            Path target = dir.resolve(timestamped);
            Files.write(target, fileBytes);
            logger.warn("Security: infected file {} moved to quarantine at {}", originalFilename, target);
            if (metricsService != null) {
                metricsService.recordSecurityRejection("malware_quarantined");
            }
        } catch (IOException e) {
            logger.error("Security: failed to quarantine infected file {}: {}", originalFilename, e.getMessage());
        }
    }

    /**
     * Get or create ClamAV client with lazy initialization
     */
    private ClamavClient getOrCreateClient() {
        if (!clientInitialized) {
            synchronized (this) {
                if (!clientInitialized) {
                    boolean initializationSuccessful = false;
                    try {
                        FileManagementProperties.VirusScanning virusConfig = properties.getVirusScanning();

                        // Validate configuration first
                        virusConfig.validate();

                        // Create ClamAV client with host and port
                        ClamavClient tempClient = new ClamavClient(
                            virusConfig.getClamavHost(),
                            virusConfig.getClamavPort(),
                            Platform.JVM_PLATFORM
                        );

                        // Test connection with timeout
                        if (!tempClient.isReachable(virusConfig.getConnectionTimeout())) {
                            throw new RuntimeException("Connection timeout to ClamAV server after " +
                                virusConfig.getConnectionTimeout() + "ms");
                        }

                        // Test basic functionality
                        tempClient.ping();

                        // Only assign if all tests pass
                        this.clamavClient = tempClient;
                        initializationSuccessful = true;

                        logger.info("Security: ClamAV client initialized successfully - Host: {}:{}, " +
                            "Connection timeout: {}ms, Read timeout: {}ms",
                            virusConfig.getClamavHost(), virusConfig.getClamavPort(),
                            virusConfig.getConnectionTimeout(), virusConfig.getReadTimeout());

                    } catch (Exception e) {
                        logger.error("Security: Failed to initialize ClamAV client", e);
                        this.clamavClient = null;
                    }

                    // Only mark as initialized if successful
                    clientInitialized = initializationSuccessful;
                }
            }
        }
        return clamavClient;
    }

    /**
     * Allows injection of a preconfigured ClamAV client for testing purposes.
     * Setting a {@code null} client marks the service as initialized but unavailable.
     *
     * @param client mock or real {@link ClamavClient}
     */
    void setClamavClientForTesting(ClamavClient client) {
        this.clamavClient = client;
        this.clientInitialized = true;
    }

    /**
     * Verifica se o escaneamento de vírus está disponível e funcional.
     * 
     * <p>Este método realiza uma verificação completa da disponibilidade do serviço:
     * <ul>
     *   <li>Verifica se o escaneamento está habilitado na configuração</li>
     *   <li>Tenta inicializar o cliente ClamAV se ainda não foi inicializado</li>
     *   <li>Valida conexão com o servidor ClamAV</li>
     * </ul>
     *
     * <p><strong>Uso Típico:</strong>
     * Ideal para health checks e determinação condicional se escaneamento deve ser aplicado.
     * Não é necessário chamar antes de scanFile() - este já faz as verificações internas.
     *
     * @return true se escaneamento está habilitado E cliente ClamAV está disponível, false caso contrário
     * @since 1.0.0
     * @see #isHealthy()
     * @see #getHealthDetails()
     */
    public boolean isVirusScanningAvailable() {
        return properties.getVirusScanning().isEnabled() && getOrCreateClient() != null;
    }

    /**
     * Obtém informações de versão do servidor ClamAV conectado.
     * 
     * <p>Este método é útil para:
     * <ul>
     *   <li><strong>Diagnóstico</strong>: Verificar versão do ClamAV em uso</li>
     *   <li><strong>Monitoramento</strong>: Incluir versão em health checks</li>
     *   <li><strong>Auditoria</strong>: Registrar versão do scanner nos logs</li>
     * </ul>
     *
     * <p><strong>Comportamento:</strong>
     * <ul>
     *   <li>Se cliente não estiver disponível, retorna mensagem indicativa</li>
     *   <li>Em caso de erro de comunicação, retorna descrição do erro</li>
     *   <li>Não lança exceções - sempre retorna string descritiva</li>
     * </ul>
     *
     * @return String contendo versão do ClamAV (ex: "ClamAV 0.103.8") ou mensagem de erro/indisponibilidade
     * @since 1.0.0
     * @see #isVirusScanningAvailable()
     */
    public String getClamavVersion() {
        ClamavClient client = getOrCreateClient();
        if (client == null) {
            return "ClamAV client not available";
        }

        try {
            return client.version();
        } catch (ClamavException e) {
            logger.error("Failed to get ClamAV version", e);
            return "Version check failed: " + e.getMessage();
        }
    }

    /**
     * Executa health check rápido da conectividade com servidor ClamAV.
     * 
     * <p>Este método é otimizado para health checks frequentes:
     * <ul>
     *   <li><strong>Timeout Curto</strong>: Usa timeout de 1 segundo (mais rápido que operações normais)</li>
     *   <li><strong>Non-blocking</strong>: Não trava a thread chamadora por muito tempo</li>
     *   <li><strong>Exception-safe</strong>: Nunca lança exceções - sempre retorna boolean</li>
     *   <li><strong>Lazy Initialization</strong>: Tenta inicializar cliente se necessário</li>
     * </ul>
     *
     * <p><strong>Uso Recomendado:</strong>
     * Ideal para endpoints de health check, monitoramento automático e dashboards.
     * Para diagnósticos detalhados, use getHealthDetails().
     *
     * @return true se ClamAV está alcançável e respondendo, false caso contrário
     * @since 1.0.0
     * @see #getHealthDetails()
     * @see #isVirusScanningAvailable()
     */
    public boolean isHealthy() {
        try {
            ClamavClient client = getOrCreateClient();
            if (client == null) {
                return false;
            }

            // Test with a shorter timeout for health checks
            return client.isReachable(1000); // 1 second timeout for health check
        } catch (Exception e) {
            logger.debug("Health check failed", e);
            return false;
        }
    }

    /**
     * Obtém informações detalhadas sobre o status de saúde do serviço de escaneamento.
     * 
     * <p>Este método fornece diagnóstico completo incluindo:
     * <ul>
     *   <li><strong>Status de Configuração</strong>: Se escaneamento está habilitado</li>
     *   <li><strong>Disponibilidade do Cliente</strong>: Se cliente ClamAV foi inicializado</li>
     *   <li><strong>Conectividade</strong>: Se servidor está alcançável</li>
     *   <li><strong>Versão</strong>: Versão do ClamAV conectado (quando disponível)</li>
     * </ul>
     *
     * <p><strong>Possíveis Retornos:</strong>
     * <ul>
     *   <li>"Virus scanning disabled" - Quando escaneamento está desabilitado</li>
     *   <li>"ClamAV client not available" - Cliente não pôde ser inicializado</li>
     *   <li>"ClamAV unreachable" - Servidor não responde</li>
     *   <li>"ClamAV connected - Version: X.X.X" - Totalmente funcional</li>
     *   <li>"Health check error: [detalhes]" - Erro inesperado</li>
     * </ul>
     *
     * <p><strong>Exception Safety:</strong>
     * Método nunca lança exceções - captura todos os erros e os inclui na mensagem de retorno.
     *
     * @return String descritiva com status detalhado do serviço de escaneamento
     * @since 1.0.0
     * @see #isHealthy()
     * @see #getClamavVersion()
     */
    public String getHealthDetails() {
        try {
            if (!properties.getVirusScanning().isEnabled()) {
                return "Virus scanning disabled";
            }

            ClamavClient client = getOrCreateClient();
            if (client == null) {
                return "ClamAV client not available";
            }

            if (isHealthy()) {
                return "ClamAV connected - Version: " + getClamavVersion();
            } else {
                return "ClamAV unreachable";
            }
        } catch (Exception e) {
            return "Health check error: " + e.getMessage();
        }
    }

    /**
     * Cleanup resources on service shutdown
     */
    @PreDestroy
    public void cleanup() {
        synchronized (this) {
            if (clamavClient != null) {
                try {
                    // ClamAV client doesn't have explicit close method,
                    // but we can clear the reference for GC
                    logger.info("Security: Cleaning up ClamAV client resources");
                    clamavClient = null;
                    clientInitialized = false;
                } catch (Exception e) {
                    logger.warn("Security: Error during ClamAV client cleanup", e);
                }
            }
        }
    }
}
