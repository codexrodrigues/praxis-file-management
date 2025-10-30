package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FileUploadMetricsService} ensuring metric names and tags are standardized.
 */
class FileUploadMetricsServiceTest {

    @Test
    void recordsMetricsWithStandardizedNamesAndTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FileUploadMetricsService service = new FileUploadMetricsService(registry);

        Timer.Sample successSample = service.startUploadTimer();
        service.recordSuccessfulUpload(successSample);

        Timer.Sample failureSample = service.startUploadTimer();
        service.recordFailedUpload(failureSample, FileErrorReason.UNSUPPORTED_FILE_TYPE);

        service.recordSecurityRejection("virus");
        service.recordSecurityRejection("virus");

        assertEquals(2.0, service.getTotalUploads(), 0.001);
        assertEquals(1.0, service.getSuccessfulUploads(), 0.001);
        assertEquals(1.0, service.getFailedUploads(), 0.001);
        assertEquals(1.0, service.getRejectionCount(FileErrorReason.UNSUPPORTED_FILE_TYPE), 0.001);

        Counter successCounter = registry.find("file.uploads").tag("result", "success").counter();
        assertNotNull(successCounter);
        assertEquals(1.0, successCounter.count(), 0.001);

        Counter failureCounter = registry.find("file.uploads").tag("result", "failed").counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count(), 0.001);

        Counter rejectionCounter = registry.find("file.uploads.rejections")
                .tag("reason", "UNSUPPORTED_FILE_TYPE")
                .counter();
        assertNotNull(rejectionCounter);
        assertEquals(1.0, rejectionCounter.count(), 0.001);

        Counter securityCounter = registry.find("file.uploads.security.rejections")
                .tag("type", "virus")
                .counter();
        assertNotNull(securityCounter);
        assertEquals(2.0, securityCounter.count(), 0.001);
    }
}
