package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for rate limiting file uploads per IP address.
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    private final FileManagementProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> concurrentUploads = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAccessTime = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitingService(FileManagementProperties properties) {
        this.properties = properties;
    }

    /**
     * Verifica se um upload é permitido para o endereço IP fornecido.
     * 
     * <p>Este método aplica controle de taxa com duas restrições:
     * <ul>
     *   <li><strong>Rate Limiting</strong>: Limites por minuto e por hora usando algoritmo token bucket</li>
     *   <li><strong>Concorrência</strong>: Limite de uploads simultâneos por IP</li>
     * </ul>
     *
     * @param ipAddress Endereço IP do cliente (não pode ser null)
     * @return true se upload é permitido, false se limites foram excedidos
     * @since 1.0.0
     */
    public boolean tryAcquireUploadPermit(String ipAddress) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }

        // Check concurrent uploads first so a rejected concurrent attempt does not consume rate-limit tokens.
        AtomicInteger concurrent = concurrentUploads.computeIfAbsent(ipAddress, k -> new AtomicInteger(0));
        int currentConcurrent = concurrent.get();

        if (currentConcurrent >= properties.getRateLimit().getMaxConcurrentUploads()) {
            logger.warn("Security: Concurrent upload limit exceeded for IP: {}", ipAddress);
            return false;
        }

        // Check rate limits
        Bucket bucket = getBucket(ipAddress);
        boolean allowed = bucket.tryConsume(1);
        
        if (!allowed) {
            logger.warn("Security: Rate limit exceeded for IP: {}", ipAddress);
            return false;
        }

        concurrent.incrementAndGet();
        return true;
    }

    /**
     * Libera um permit de upload concorrente para o IP fornecido.
     * 
     * <p>Este método deve ser chamado sempre após conclusão do upload (sucesso ou falha)
     * para garantir que o contador de uploads concorrentes seja decrementado corretamente.
     *
     * @param ipAddress Endereço IP do cliente (não pode ser null)
     * @since 1.0.0
     */
    public void releaseUploadPermit(String ipAddress) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }

        AtomicInteger concurrent = concurrentUploads.get(ipAddress);
        if (concurrent != null) {
            int current = concurrent.decrementAndGet();
            if (current <= 0) {
                concurrentUploads.remove(ipAddress, concurrent);
            }
        }
    }

    /**
     * Get or create a rate limit bucket for an IP address
     */
    private Bucket getBucket(String ipAddress) {
        // Update last access time for cleanup
        lastAccessTime.put(ipAddress, Instant.now());
        
        return buckets.computeIfAbsent(ipAddress, k -> {
            Bandwidth minuteLimit = Bandwidth.classic(
                properties.getRateLimit().getMaxUploadsPerMinute(),
                Refill.intervally(properties.getRateLimit().getMaxUploadsPerMinute(), Duration.ofMinutes(1))
            );
            
            Bandwidth hourLimit = Bandwidth.classic(
                properties.getRateLimit().getMaxUploadsPerHour(),
                Refill.intervally(properties.getRateLimit().getMaxUploadsPerHour(), Duration.ofHours(1))
            );
            
            return Bucket4j.builder()
                .addLimit(minuteLimit)
                .addLimit(hourLimit)
                .build();
        });
    }

    /**
     * Cleanup expired buckets to prevent memory leaks in enterprise environments
     * Runs every hour to clean up IPs that haven't been active for 24 hours
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupExpiredBuckets() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        int removedCount = 0;
        
        for (Map.Entry<String, Instant> entry : lastAccessTime.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                String ipAddress = entry.getKey();
                buckets.remove(ipAddress);
                concurrentUploads.remove(ipAddress);
                lastAccessTime.remove(ipAddress);
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            logger.debug("Cleaned up {} expired rate limit buckets", removedCount);
        }
    }

    /**
     * Obtém o número atual de buckets ativos para monitoramento.
     * 
     * <p>Útil para:
     * <ul>
     *   <li>Monitoramento de uso de memória</li>
     *   <li>Dashboards de administração</li>
     *   <li>Alertas de performance</li>
     * </ul>
     *
     * @return Número de buckets de rate limiting ativos na memória
     * @since 1.0.0
     */
    public int getActiveBucketCount() {
        return buckets.size();
    }

    /**
     * Force cleanup all buckets (useful for testing)
     */
    @PreDestroy
    public void cleanup() {
        buckets.clear();
        concurrentUploads.clear();
        lastAccessTime.clear();
        logger.info("Rate limiting service cleaned up");
    }

    /**
     * Obtém o número de uploads restantes para um IP no minuto atual.
     * 
     * <p>Retorna o número de tokens disponíveis no bucket do IP, indicando
     * quantos uploads imediatos ainda são permitidos antes de atingir o limite.
     *
     * @param ipAddress Endereço IP para consulta (não pode ser null)
     * @return Número de uploads restantes no minuto atual, ou Long.MAX_VALUE se rate limiting desabilitado
     * @since 1.0.0
     */
    public long getRemainingUploadsPerMinute(String ipAddress) {
        if (!properties.getRateLimit().isEnabled()) {
            return Long.MAX_VALUE;
        }
        
        Bucket bucket = getBucket(ipAddress);
        return bucket.getAvailableTokens();
    }

    /**
     * Reseta os limites de taxa para um endereço IP (para testes ou propósitos administrativos).
     * 
     * <p>Remove todos os dados de rate limiting para o IP especificado:
     * <ul>
     *   <li>Bucket de tokens</li>
     *   <li>Contador de uploads concorrentes</li>
     * </ul>
     *
     * @param ipAddress Endereço IP para reset (não pode ser null)
     * @since 1.0.0
     */
    public void resetRateLimits(String ipAddress) {
        buckets.remove(ipAddress);
        concurrentUploads.remove(ipAddress);
        lastAccessTime.remove(ipAddress);
        logger.info("Rate limits reset for IP: {}", ipAddress);
    }

    /**
     * Obtém o número atual de uploads concorrentes para um IP.
     * 
     * <p>Retorna o contador atual de uploads em andamento para o IP especificado.
     * Útil para monitoramento e debugging.
     *
     * @param ipAddress Endereço IP para consulta (não pode ser null)
     * @return Número de uploads concorrentes ativos para o IP, ou 0 se nenhum
     * @since 1.0.0
     */
    public int getCurrentConcurrentUploads(String ipAddress) {
        AtomicInteger concurrent = concurrentUploads.get(ipAddress);
        return concurrent != null ? concurrent.get() : 0;
    }
}
