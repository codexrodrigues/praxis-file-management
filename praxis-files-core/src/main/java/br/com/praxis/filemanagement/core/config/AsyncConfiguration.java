package br.com.praxis.filemanagement.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for enterprise-grade file processing
 * Optimized for high-throughput file upload scenarios
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Autowired
    private FileManagementProperties properties;

    /**
     * Custom executor for file processing tasks.
     * 
     * <p>Tuned for enterprise workloads with proper resource management and
     * optimized for high-throughput file upload scenarios. This executor is
     * configured with:
     * <ul>
     *   <li><strong>Core Pool Size</strong>: 4 threads always available</li>
     *   <li><strong>Max Pool Size</strong>: Scales to 2x CPU cores under load</li>
     *   <li><strong>Queue Capacity</strong>: 100 pending tasks buffer</li>
     *   <li><strong>Keep Alive</strong>: 5 minutes for idle thread cleanup</li>
     *   <li><strong>Rejection Policy</strong>: Enterprise-grade with monitoring</li>
     * </ul>
     * 
     * <p>The executor includes enterprise features:
     * <ul>
     *   <li>Core thread timeout for resource efficiency</li>
     *   <li>Graceful shutdown with task completion waiting</li>
     *   <li>Custom rejection handler with fallback strategies</li>
     *   <li>Comprehensive logging for monitoring and debugging</li>
     * </ul>
     * 
     * @return Configured ThreadPoolTaskExecutor for file processing operations
     * @since 1.0.0
     */
    @Bean(name = "fileProcessingExecutor")
    public Executor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - start with this many threads
        executor.setCorePoolSize(4);
        
        // Max pool size - scale up to this when needed
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        
        // Queue capacity - buffer for pending tasks
        executor.setQueueCapacity(100);
        
        // Thread naming for monitoring and debugging
        executor.setThreadNamePrefix("FileProcessor-");
        
        // Keep alive time for idle threads
        executor.setKeepAliveSeconds(300); // 5 minutes
        
        // Allow core threads to timeout (Java 17+ optimization)
        executor.setAllowCoreThreadTimeOut(true);
        
        // Custom rejection policy for enterprise resilience
        executor.setRejectedExecutionHandler(new EnterpriseRejectionHandler());
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        logger.info("File processing executor initialized with core={}, max={}, queue={}", 
                   executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * Custom executor for virus scanning tasks with separate thread pool.
     * 
     * <p>Virus scanning is I/O bound and may have different performance
     * characteristics compared to file processing, requiring a dedicated
     * executor configuration:
     * <ul>
     *   <li><strong>Core Pool Size</strong>: 2 threads for baseline scanning</li>
     *   <li><strong>Max Pool Size</strong>: 8 threads maximum for I/O operations</li>
     *   <li><strong>Queue Capacity</strong>: 50 pending scans buffer</li>
     *   <li><strong>Keep Alive</strong>: 10 minutes for I/O thread efficiency</li>
     *   <li><strong>Rejection Policy</strong>: CallerRunsPolicy (blocking acceptable)</li>
     * </ul>
     * 
     * <p>This executor is optimized for:
     * <ul>
     *   <li>I/O intensive virus scanning operations</li>
     *   <li>Network communication with virus scanning services</li>
     *   <li>Blocking operations where caller-runs is appropriate</li>
     *   <li>Separate resource isolation from file processing</li>
     * </ul>
     * 
     * @return Configured ThreadPoolTaskExecutor for virus scanning operations
     * @since 1.0.0
     */
    @Bean(name = "virusScanningExecutor")
    public Executor virusScanningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Virus scanning is I/O bound, can handle more threads
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("VirusScanner-");
        executor.setKeepAliveSeconds(600); // 10 minutes
        executor.setAllowCoreThreadTimeOut(true);
        
        // Use caller runs policy for virus scanning (blocking is acceptable)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        logger.info("Virus scanning executor initialized with core={}, max={}", 
                   executor.getCorePoolSize(), executor.getMaxPoolSize());
        
        return executor;
    }

    /**
     * Enterprise-grade rejection handler with monitoring and graceful degradation
     */
    private static class EnterpriseRejectionHandler implements RejectedExecutionHandler {
        private static final Logger rejectionLogger = LoggerFactory.getLogger(EnterpriseRejectionHandler.class);

        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            // Log rejection for monitoring
            rejectionLogger.warn("File processing task rejected. Active: {}, Pool size: {}, Queue size: {}", 
                               executor.getActiveCount(), executor.getPoolSize(), executor.getQueue().size());
            
            // Try to execute in caller thread as fallback
            if (!executor.isShutdown()) {
                try {
                    // Attempt to put back in queue with timeout
                    boolean added = executor.getQueue().offer(task);
                    if (!added) {
                        rejectionLogger.error("Failed to requeue rejected task, executing in caller thread");
                        task.run();
                    }
                } catch (Exception e) {
                    rejectionLogger.error("Failed to handle rejected task", e);
                    throw new RuntimeException("File processing overloaded", e);
                }
            }
        }
    }
}
