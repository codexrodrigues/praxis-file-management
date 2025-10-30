package br.com.praxis.filemanagement.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreadSafeFileNamingServiceConcurrencyTest {

    private final ThreadSafeFileNamingService service = new ThreadSafeFileNamingService();

    @Test
    @DisplayName("generates unique names under concurrent stress")
    void generatesUniqueNamesUnderConcurrentStress(@TempDir Path tempDir) throws Exception {
        int tasks = 200;
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < tasks; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                ThreadSafeFileNamingService.UniqueNameResult result =
                    service.generateUniqueNameForRename("file.txt", tempDir);
                Files.createFile(tempDir.resolve(result.finalName()));
                return result.finalName();
            }));
        }

        start.countDown();

        Set<String> names = new HashSet<>();
        for (Future<String> f : futures) {
            names.add(f.get(5, TimeUnit.SECONDS));
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(tasks, names.size(), "All generated names should be unique");
    }
}
