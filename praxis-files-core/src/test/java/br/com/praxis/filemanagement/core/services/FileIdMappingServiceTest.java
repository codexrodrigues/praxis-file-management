package br.com.praxis.filemanagement.core.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileIdMappingServiceTest {

    private final FileIdMappingService service = new FileIdMappingService();

    @Test
    @DisplayName("generates secure hash id and stores mapping")
    void generatesSecureHashIdAndStoresMapping(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("stored.txt"));

        String fileId = service.generateFileId(
            "stored.txt",
            "Original Report.txt",
            tempDir.toString(),
            123L,
            "text/plain",
            FileIdMappingService.FileIdStrategy.SECURE_HASH
        );

        assertTrue(fileId.startsWith("hash_"));
        assertEquals(21, fileId.length());

        FileIdMappingService.FileMapping mapping = service.getFileMapping(fileId);
        assertNotNull(mapping);
        assertEquals("stored.txt", mapping.serverFilename());
        assertEquals("Original Report.txt", mapping.originalFilename());
        assertEquals(file, mapping.getFullPath());
        assertTrue(mapping.isValid());
    }

    @Test
    @DisplayName("generates uuid based and hybrid ids with sanitized prefixes")
    void generatesUuidBasedAndHybridIdsWithSanitizedPrefixes(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("server-a.bin"));
        Files.createFile(tempDir.resolve("server-b.bin"));

        String uuidId = service.generateFileId(
            "server-a.bin",
            "My Invoice 2026.pdf",
            tempDir.toString(),
            10L,
            "application/pdf",
            FileIdMappingService.FileIdStrategy.UUID_BASED
        );

        String hybridId = service.generateFileId(
            "server-b.bin",
            "***.pdf",
            tempDir.toString(),
            10L,
            "application/pdf",
            FileIdMappingService.FileIdStrategy.HYBRID
        );

        assertTrue(uuidId.startsWith("myinvoic_"));
        assertTrue(hybridId.startsWith("file_"));
        assertEquals(3, hybridId.split("_").length);
    }

    @Test
    @DisplayName("resolves path validates ids and removes mappings")
    void resolvesPathValidatesIdsAndRemovesMappings(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("entry.txt"));
        String fileId = service.generateFileId(
            "entry.txt",
            "entry.txt",
            tempDir.toString(),
            5L,
            "text/plain"
        );

        assertEquals(file, service.resolveFilePath(fileId));
        assertTrue(service.isValidFileId(fileId));
        assertTrue(service.removeMapping(fileId));
        assertFalse(service.removeMapping(fileId));
        assertFalse(service.isValidFileId(fileId));
        assertNull(service.resolveFilePath(fileId));
    }

    @Test
    @DisplayName("invalid physical file invalidates and evicts mapping")
    void invalidPhysicalFileInvalidatesAndEvictsMapping(@TempDir Path tempDir) {
        String fileId = service.generateFileId(
            "missing.txt",
            "missing.txt",
            tempDir.toString(),
            1L,
            "text/plain"
        );

        assertNull(service.getFileMapping(fileId));
        assertFalse(service.isValidFileId(fileId));
    }

    @Test
    @DisplayName("reports mapping statistics")
    void reportsMappingStatistics(@TempDir Path tempDir) throws Exception {
        Files.createFile(tempDir.resolve("valid.txt"));

        service.generateFileId("valid.txt", "valid.txt", tempDir.toString(), 1L, "text/plain");
        service.generateFileId("missing.txt", "missing.txt", tempDir.toString(), 1L, "text/plain");

        FileIdMappingService.MappingStatistics statistics = service.getStatistics();

        assertEquals(2, statistics.totalMappings());
        assertEquals(1, statistics.validMappings());
        assertEquals(1, statistics.getInvalidMappings());
        assertEquals(50.0, statistics.getValidPercentage());
    }

    @Test
    @DisplayName("mapping and statistics records expose helpers")
    void mappingAndStatisticsRecordsExposeHelpers(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("mapped.txt"));
        FileIdMappingService.FileMapping mapping = new FileIdMappingService.FileMapping(
            "id-1",
            "mapped.txt",
            "mapped.txt",
            tempDir.toString(),
            java.time.LocalDateTime.now(),
            8L,
            "text/plain"
        );

        assertEquals(file, mapping.getFullPath());
        assertTrue(mapping.isValid());

        FileIdMappingService.MappingStatistics empty = new FileIdMappingService.MappingStatistics(0, 0);
        assertEquals(0, empty.getInvalidMappings());
        assertEquals(0.0, empty.getValidPercentage());
    }

    @Test
    @DisplayName("rejects invalid parameters")
    void rejectsInvalidParameters() {
        assertThrows(NullPointerException.class, () ->
            service.generateFileId(null, "a", "/tmp", 1L, "text/plain"));
        assertThrows(IllegalArgumentException.class, () ->
            service.generateFileId(" ", "a", "/tmp", 1L, "text/plain"));
        assertThrows(IllegalArgumentException.class, () ->
            service.generateFileId("a", " ", "/tmp", 1L, "text/plain"));
        assertThrows(IllegalArgumentException.class, () ->
            service.generateFileId("a", "b", " ", 1L, "text/plain"));
        assertThrows(NullPointerException.class, () -> service.getFileMapping(null));
        assertThrows(NullPointerException.class, () -> service.removeMapping(null));
    }
}
