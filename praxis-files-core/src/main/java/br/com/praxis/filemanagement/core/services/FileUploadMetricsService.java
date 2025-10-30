package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking file upload metrics and security rejection rates.
 * Only enabled when Micrometer is available on classpath AND MeterRegistry bean is present.
 */
@Service
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class FileUploadMetricsService {

    private static final String METRIC_UPLOADS = "file.uploads";
    private static final String METRIC_UPLOAD_DURATION = "file.uploads.duration";
    private static final String METRIC_REJECTIONS = "file.uploads.rejections";
    private static final String METRIC_SECURITY_REJECTIONS = "file.uploads.security.rejections";
    private static final String TAG_RESULT = "result";
    private static final String TAG_REASON = "reason";
    private static final String TAG_TYPE = "type";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> rejectionCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> securityRejectionCounters = new ConcurrentHashMap<>();

    private final Counter totalUploadsCounter;
    private final Counter successfulUploadsCounter;
    private final Counter failedUploadsCounter;
    private final Timer uploadTimer;

    public FileUploadMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.totalUploadsCounter = Counter.builder(METRIC_UPLOADS)
                .tag(TAG_RESULT, "total")
                .description("Total number of file upload attempts")
                .register(meterRegistry);

        this.successfulUploadsCounter = Counter.builder(METRIC_UPLOADS)
                .tag(TAG_RESULT, "success")
                .description("Number of successful file uploads")
                .register(meterRegistry);

        this.failedUploadsCounter = Counter.builder(METRIC_UPLOADS)
                .tag(TAG_RESULT, "failed")
                .description("Number of failed file uploads")
                .register(meterRegistry);

        this.uploadTimer = Timer.builder(METRIC_UPLOAD_DURATION)
                .description("File upload duration")
                .register(meterRegistry);
    }

    /**
     * Record a file upload attempt
     */
    public Timer.Sample startUploadTimer() {
        totalUploadsCounter.increment();
        return Timer.start(meterRegistry);
    }

    /**
     * Record a successful upload
     */
    public void recordSuccessfulUpload(Timer.Sample sample) {
        successfulUploadsCounter.increment();
        sample.stop(uploadTimer);
    }

    /**
     * Record a failed upload with reason
     */
    public void recordFailedUpload(Timer.Sample sample, FileErrorReason reason) {
        failedUploadsCounter.increment();
        sample.stop(uploadTimer);

        if (reason != null && reason != FileErrorReason.NONE) {
            Counter rejectionCounter = rejectionCounters.computeIfAbsent(
                reason.name(),
                k -> Counter.builder(METRIC_REJECTIONS)
                        .tag(TAG_REASON, reason.name())
                        .description("File upload rejections by reason")
                        .register(meterRegistry)
            );
            rejectionCounter.increment();
        }
    }

    /**
     * Record a security rejection
     */
    public void recordSecurityRejection(String securityType) {
        Counter securityCounter = securityRejectionCounters.computeIfAbsent(
            securityType,
            k -> Counter.builder(METRIC_SECURITY_REJECTIONS)
                    .tag(TAG_TYPE, securityType)
                    .description("Security-related file upload rejections")
                    .register(meterRegistry)
        );
        securityCounter.increment();
    }

    /**
     * Get total upload count
     */
    public double getTotalUploads() {
        return totalUploadsCounter.count();
    }

    /**
     * Get successful upload count
     */
    public double getSuccessfulUploads() {
        return successfulUploadsCounter.count();
    }

    /**
     * Get failed upload count
     */
    public double getFailedUploads() {
        return failedUploadsCounter.count();
    }

    /**
     * Get rejection count by reason
     */
    public double getRejectionCount(FileErrorReason reason) {
        Counter counter = rejectionCounters.get(reason.name());
        return counter != null ? counter.count() : 0;
    }
}
