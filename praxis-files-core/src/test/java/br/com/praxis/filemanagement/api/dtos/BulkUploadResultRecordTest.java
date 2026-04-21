package br.com.praxis.filemanagement.api.dtos;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkUploadResultRecordTest {

    @Test
    void fromResultsCalculatesStatisticsAndFailFastFlags() {
        Instant start = Instant.parse("2026-03-15T15:00:00Z");
        Instant end = Instant.parse("2026-03-15T15:00:02Z");
        List<FileUploadResultRecord> results = List.of(
                FileUploadResultRecord.success("a.txt", "a-1.txt", "id-a", 10L, "text/plain"),
                FileUploadResultRecord.error("b.txt", FileErrorReason.INVALID_TYPE, "bad"),
                FileUploadResultRecord.error("c.txt", FileErrorReason.BULK_UPLOAD_CANCELLED, "cancelled"));

        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(results, start, end);

        assertEquals(3, bulk.totalProcessed());
        assertEquals(1, bulk.totalSuccess());
        assertEquals(2, bulk.totalFailed());
        assertEquals(1, bulk.totalCancelled());
        assertFalse(bulk.overallSuccess());
        assertTrue(bulk.wasFailFastTriggered());
        assertEquals(2000L, bulk.processingTimeMs());
        assertEquals(10L, bulk.totalSizeBytes());
        assertEquals(1, bulk.getSuccessfulResults().size());
        assertEquals(2, bulk.getFailedResults().size());
        assertEquals(1, bulk.getCancelledResults().size());
        assertTrue(bulk.metadata().containsKey("successRate"));
    }

    @Test
    void builderCreatesRecordAndAutoCalculatesMissingValues() {
        List<FileUploadResultRecord> results = List.of(
                FileUploadResultRecord.success("a.txt", "a-1.txt", "id-a", 10L, "text/plain"),
                FileUploadResultRecord.error("b.txt", FileErrorReason.UNKNOWN_ERROR, "boom"));

        BulkUploadResultRecord bulk = BulkUploadResultRecord.builder()
                .results(results)
                .overallSuccess(false)
                .startTimestamp(Instant.parse("2026-03-15T15:00:00Z"))
                .endTimestamp(Instant.parse("2026-03-15T15:00:01Z"))
                .metadata(Map.of("mode", "test"))
                .build();

        assertEquals(2, bulk.totalProcessed());
        assertEquals(1, bulk.totalSuccess());
        assertEquals(1, bulk.totalFailed());
        assertEquals("test", bulk.metadata().get("mode"));
        assertEquals(50.0, bulk.getSuccessRate());
    }

    @Test
    void constructorRejectsInvalidArguments() {
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class, () -> new BulkUploadResultRecord(
                null, 0, 0, 0, true, now, now, 0L, 0L, Map.of(), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new BulkUploadResultRecord(
                List.of(), 0, 0, 0, null, now, now, 0L, 0L, Map.of(), 0, false));
    }

    @Test
    void fromResultsHandlesEmptyList() {
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of());

        assertEquals(0, bulk.totalProcessed());
        assertTrue(bulk.overallSuccess());
        assertEquals(0, bulk.totalCancelled());
        assertFalse(bulk.wasFailFastTriggered());
    }
}
