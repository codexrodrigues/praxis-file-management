package br.com.praxis.filemanagement.web.error;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics recorder for file-related errors.
 * Exposes a Prometheus counter:
 *   file_errors_total{reason="...",endpoint="..."}
 */
@Service
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class FileErrorMetricsService {

    private static final String METRIC_NAME = "file_errors_total";
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public FileErrorMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Increment error counter with reason and endpoint tags.
     *
     * @param reason   the FileErrorReason associated with the error
     * @param endpoint the HTTP endpoint path
     */
    public void increment(FileErrorReason reason, String endpoint) {
        String reasonTag = reason != null ? reason.name() : FileErrorReason.UNKNOWN_ERROR.name();
        String endpointTag = endpoint != null ? endpoint : "unknown";
        String key = reasonTag + "|" + endpointTag;

        Counter counter = counters.computeIfAbsent(key,
                k -> Counter.builder(METRIC_NAME)
                        .tag("reason", reasonTag)
                        .tag("endpoint", endpointTag)
                        .description("File errors by reason and endpoint")
                        .register(meterRegistry)
        );
        counter.increment();
    }
}
