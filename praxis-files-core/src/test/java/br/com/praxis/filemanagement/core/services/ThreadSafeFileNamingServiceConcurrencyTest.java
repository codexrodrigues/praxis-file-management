package br.com.praxis.filemanagement.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        for (String name : names) {
            service.releaseReservedName(tempDir, name);
        }
    }

    @Test
    @DisplayName("returns original strategy when target name is available")
    void returnsOriginalStrategyWhenTargetNameIsAvailable(@TempDir Path tempDir) throws Exception {
        ThreadSafeFileNamingService.UniqueNameResult result =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertEquals("report.txt", result.finalName());
        assertEquals(1, result.attemptsUsed());
        assertEquals(ThreadSafeFileNamingService.NamingStrategy.ORIGINAL, result.strategy());
        assertEquals("report", result.components().baseName());
        assertEquals("txt", result.components().extension());

        service.releaseReservedName(tempDir, result.finalName());
    }

    @Test
    @DisplayName("returns incremental strategy when original name already exists")
    void returnsIncrementalStrategyWhenOriginalNameAlreadyExists(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("report.txt"));

        ThreadSafeFileNamingService.UniqueNameResult result =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertEquals("report(1).txt", result.finalName());
        assertEquals(2, result.attemptsUsed());
        assertEquals(ThreadSafeFileNamingService.NamingStrategy.INCREMENTAL, result.strategy());

        service.releaseReservedName(tempDir, result.finalName());
    }

    @Test
    @DisplayName("falls back to hybrid strategy after exhausting incremental names")
    void fallsBackToHybridStrategyAfterExhaustingIncrementalNames(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("report.txt"));
        for (int i = 1; i <= 1000; i++) {
            Files.createFile(tempDir.resolve("report(" + i + ").txt"));
        }

        ThreadSafeFileNamingService.UniqueNameResult result =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertEquals(ThreadSafeFileNamingService.NamingStrategy.HYBRID, result.strategy());
        assertTrue(result.finalName().startsWith("report_"));
        assertTrue(result.finalName().endsWith(".txt"));

        service.releaseReservedName(tempDir, result.finalName());
    }

    @Test
    @DisplayName("releases reserved name so it can be reused")
    void releasesReservedNameSoItCanBeReused(@TempDir Path tempDir) throws Exception {
        ThreadSafeFileNamingService.UniqueNameResult first =
            service.generateUniqueNameForRename("report.txt", tempDir);
        ThreadSafeFileNamingService.UniqueNameResult second =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertEquals("report.txt", first.finalName());
        assertEquals("report(1).txt", second.finalName());

        service.releaseReservedName(tempDir, first.finalName());

        ThreadSafeFileNamingService.UniqueNameResult reused =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertEquals("report.txt", reused.finalName());
        assertEquals(ThreadSafeFileNamingService.NamingStrategy.ORIGINAL, reused.strategy());

        service.releaseReservedName(tempDir, second.finalName());
        service.releaseReservedName(tempDir, reused.finalName());
    }

    @Test
    @DisplayName("make unique uses uuid fallback and preserves extension")
    void makeUniqueUsesUuidFallbackAndPreservesExtension() {
        ThreadSafeFileNamingService.UniqueNameResult withExtension =
            service.generateUniqueNameForMakeUnique("report.txt");
        ThreadSafeFileNamingService.UniqueNameResult withoutExtension =
            service.generateUniqueNameForMakeUnique("README");

        assertEquals(ThreadSafeFileNamingService.NamingStrategy.RANDOM_UUID, withExtension.strategy());
        assertEquals(ThreadSafeFileNamingService.NamingStrategy.RANDOM_UUID, withoutExtension.strategy());
        assertTrue(withExtension.finalName().endsWith(".txt"));
        assertFalse(withoutExtension.finalName().contains("."));
        assertEquals(withExtension.components().baseName(), withExtension.finalName().replace(".txt", ""));
        assertEquals(withoutExtension.components().baseName(), withoutExtension.finalName());
    }

    @Test
    @DisplayName("ignores release requests with null or blank values")
    void ignoresReleaseRequestsWithNullOrBlankValues(@TempDir Path tempDir) throws Exception {
        ThreadSafeFileNamingService.UniqueNameResult result =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertDoesNotThrow(() -> service.releaseReservedName(null, result.finalName()));
        assertDoesNotThrow(() -> service.releaseReservedName(tempDir, null));
        assertDoesNotThrow(() -> service.releaseReservedName(tempDir, " "));
        assertDoesNotThrow(() -> service.releaseReservedName(tempDir.resolve("other"), result.finalName()));

        ThreadSafeFileNamingService.UniqueNameResult next =
            service.generateUniqueNameForRename("report.txt", tempDir);

        assertEquals("report(1).txt", next.finalName());

        service.releaseReservedName(tempDir, result.finalName());
        service.releaseReservedName(tempDir, next.finalName());
    }

    @Test
    @DisplayName("removes directory reservation bucket after last release")
    void removesDirectoryReservationBucketAfterLastRelease(@TempDir Path tempDir) throws Exception {
        ThreadSafeFileNamingService.UniqueNameResult result =
            service.generateUniqueNameForRename("report.txt", tempDir);

        service.releaseReservedName(tempDir, result.finalName());

        Field field = ThreadSafeFileNamingService.class.getDeclaredField("reservedNamesByDirectory");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, Set<String>> reserved = (Map<Path, Set<String>>) field.get(service);

        assertTrue(reserved.isEmpty());
    }

    @Test
    @DisplayName("rejects invalid parameters")
    void rejectsInvalidParameters(@TempDir Path tempDir) throws IOException {
        Path regularFile = Files.createFile(tempDir.resolve("plain.txt"));
        IllegalArgumentException blankName =
            assertThrows(IllegalArgumentException.class, () -> service.generateUniqueNameForRename(" ", tempDir));
        IllegalArgumentException missingDirectory =
            assertThrows(IllegalArgumentException.class,
                () -> service.generateUniqueNameForRename("report.txt", tempDir.resolve("missing")));
        IllegalArgumentException nonDirectory =
            assertThrows(IllegalArgumentException.class,
                () -> service.generateUniqueNameForRename("report.txt", regularFile));
        NullPointerException nullFilename =
            assertThrows(NullPointerException.class, () -> service.generateUniqueNameForRename(null, tempDir));
        NullPointerException nullDirectory =
            assertThrows(NullPointerException.class, () -> service.generateUniqueNameForRename("report.txt", null));
        NullPointerException nullMakeUnique =
            assertThrows(NullPointerException.class, () -> service.generateUniqueNameForMakeUnique(null));

        assertTrue(blankName.getMessage().contains("vazio"));
        assertTrue(missingDirectory.getMessage().contains("deve existir"));
        assertTrue(nonDirectory.getMessage().contains("diretório"));
        assertInstanceOf(NullPointerException.class, nullFilename);
        assertInstanceOf(NullPointerException.class, nullDirectory);
        assertInstanceOf(NullPointerException.class, nullMakeUnique);
    }
}
