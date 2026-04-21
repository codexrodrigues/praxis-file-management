package br.com.praxis.filemanagement.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigurationTest {

    @Test
    void configuresFileProcessingExecutor() {
        AsyncConfiguration configuration = new AsyncConfiguration();
        ReflectionTestUtils.setField(configuration, "properties", new FileManagementProperties());

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuration.fileProcessingExecutor();

        assertEquals(4, executor.getCorePoolSize());
        assertEquals(Runtime.getRuntime().availableProcessors() * 2, executor.getMaxPoolSize());
        assertEquals(100, executor.getQueueCapacity());
        assertEquals(300, executor.getKeepAliveSeconds());
        assertTrue(executor.getThreadNamePrefix().startsWith("FileProcessor-"));
        assertTrue(executor.getThreadPoolExecutor().allowsCoreThreadTimeOut());
        executor.shutdown();
    }

    @Test
    void configuresVirusScanningExecutor() {
        AsyncConfiguration configuration = new AsyncConfiguration();
        ReflectionTestUtils.setField(configuration, "properties", new FileManagementProperties());

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuration.virusScanningExecutor();

        assertEquals(2, executor.getCorePoolSize());
        assertEquals(8, executor.getMaxPoolSize());
        assertEquals(50, executor.getQueueCapacity());
        assertEquals(600, executor.getKeepAliveSeconds());
        assertTrue(executor.getThreadNamePrefix().startsWith("VirusScanner-"));
        assertTrue(executor.getThreadPoolExecutor().allowsCoreThreadTimeOut());
        assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class,
            executor.getThreadPoolExecutor().getRejectedExecutionHandler());

        executor.shutdown();
    }

    @Test
    void enterpriseRejectionHandlerFallsBackToCallerThreadWhenQueueIsFull() throws Exception {
        Class<?> handlerClass = Class.forName(
            "br.com.praxis.filemanagement.core.config.AsyncConfiguration$EnterpriseRejectionHandler"
        );
        Constructor<?> constructor = handlerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        RejectedExecutionHandler handler = (RejectedExecutionHandler) constructor.newInstance();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            1,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(1)
        );

        AtomicBoolean taskRan = new AtomicBoolean(false);
        executor.getQueue().offer(() -> { });

        handler.rejectedExecution(() -> taskRan.set(true), executor);

        assertTrue(taskRan.get());
        executor.shutdownNow();
    }
}
