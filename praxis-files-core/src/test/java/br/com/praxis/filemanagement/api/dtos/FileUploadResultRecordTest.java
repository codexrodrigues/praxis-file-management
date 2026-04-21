package br.com.praxis.filemanagement.api.dtos;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadResultRecordTest {

    @Test
    void successFactoryCreatesSuccessfulRecord() {
        FileUploadResultRecord result = FileUploadResultRecord.success(
                "doc.txt", "srv.txt", "file-id", 12L, "text/plain");

        assertEquals("doc.txt", result.originalFilename());
        assertEquals("srv.txt", result.serverFilename());
        assertEquals("file-id", result.fileId());
        assertEquals(12L, result.fileSize());
        assertEquals("text/plain", result.mimeType());
        assertTrue(result.success());
        assertTrue(result.metadata().isEmpty());
        assertNotNull(result.uploadTimestamp());
    }

    @Test
    void errorFactoriesCreateFailureRecords() {
        FileUploadResultRecord basicError = FileUploadResultRecord.error(
                "doc.txt", FileErrorReason.INVALID_TYPE, "bad type");
        FileUploadResultRecord sizedError = FileUploadResultRecord.error(
                "doc.txt", FileErrorReason.FILE_TOO_LARGE, "too large", 99L);

        assertFalse(basicError.success());
        assertEquals(FileErrorReason.INVALID_TYPE, basicError.errorReason());
        assertEquals(0L, basicError.fileSize());

        assertFalse(sizedError.success());
        assertEquals(FileErrorReason.FILE_TOO_LARGE, sizedError.errorReason());
        assertEquals(99L, sizedError.fileSize());
    }

    @Test
    void builderBuildsRecordAndDefensivelyCopiesMetadata() {
        Instant timestamp = Instant.parse("2026-03-15T15:00:00Z");
        Map<String, Object> metadata = Map.of("checksum", "abc");

        FileUploadResultRecord result = FileUploadResultRecord.builder()
                .originalFilename("doc.txt")
                .serverFilename("srv.txt")
                .fileId("id")
                .fileSize(10L)
                .mimeType("text/plain")
                .success(true)
                .uploadTimestamp(timestamp)
                .metadata(metadata)
                .build();

        assertEquals(timestamp, result.uploadTimestamp());
        assertEquals("abc", result.metadata().get("checksum"));
    }

    @Test
    void constructorValidatesRequiredFields() {
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class, () -> new FileUploadResultRecord(
                "", null, null, 0L, null, true, null, null, now, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new FileUploadResultRecord(
                "doc.txt", null, null, -1L, null, true, null, null, now, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new FileUploadResultRecord(
                "doc.txt", null, null, 0L, null, null, null, null, now, Map.of()));
    }

    @Test
    void constructorFillsTimestampWhenMissing() {
        FileUploadResultRecord result = new FileUploadResultRecord(
                "doc.txt", null, null, 1L, "text/plain", true, null, null, null, null);

        assertNotNull(result.uploadTimestamp());
        assertTrue(result.metadata().isEmpty());
    }
}
