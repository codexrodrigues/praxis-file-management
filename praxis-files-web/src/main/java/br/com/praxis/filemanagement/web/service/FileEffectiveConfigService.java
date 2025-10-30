package br.com.praxis.filemanagement.web.service;

import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord;
import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord.BulkConfig;
import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord.Metadata;
import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord.QuotasConfig;
import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord.RateLimitConfig;
import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.utils.ErrorMessageUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service responsible for aggregating the effective upload configuration that
 * the server will apply for the current context.
 */
@Service
public class FileEffectiveConfigService {

    private final FileManagementProperties properties;

    public FileEffectiveConfigService(FileManagementProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds the effective upload configuration considering global defaults and
     * optional tenant/user overrides.
     *
     * @param tenantId optional tenant identifier
     * @param userId   optional user identifier
     * @return record containing the effective configuration
     */
    public EffectiveUploadConfigRecord getEffectiveConfig(String tenantId, String userId) {
        FileUploadOptionsRecord options = buildOptions();
        BulkConfig bulk = buildBulk();
        RateLimitConfig rateLimit = buildRateLimit();
        QuotasConfig quotas = buildQuotas(tenantId, userId);
        Map<String, String> messages = ErrorMessageUtils.getAllMessages();
        String version = FileEffectiveConfigService.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "dev";
        }
        String locale = Locale.getDefault().toLanguageTag();
        Metadata metadata = new Metadata(version, locale);

        return new EffectiveUploadConfigRecord(options, bulk, rateLimit, quotas, messages, metadata);
    }

    private FileUploadOptionsRecord buildOptions() {
        List<String> mimeTypes = properties.getSecurity().getAllowedMimeTypes().stream().toList();
        long maxMb = properties.getMaxFileSizeBytes() / (1024 * 1024);

        return FileUploadOptionsRecord.builder()
            .allowedExtensions(List.of())
            .acceptMimeTypes(mimeTypes)
            .nameConflictPolicy(properties.getDefaultConflictPolicy())
            .maxUploadSizeMb(maxMb)
            .strictValidation(properties.getSecurity().isStrictValidation())
            .targetDirectory(null)
            .enableVirusScanning(properties.getVirusScanning().isEnabled())
            .customMetadata(Map.of())
            .failFastMode(properties.getBulkUpload().isFailFastMode())
            .build();
    }

    private BulkConfig buildBulk() {
        return new BulkConfig(
            properties.getBulkUpload().isFailFastMode(),
            properties.getBulkUpload().getMaxFilesPerBatch(),
            properties.getBulkUpload().getMaxConcurrentUploads()
        );
    }

    private RateLimitConfig buildRateLimit() {
        return new RateLimitConfig(
            properties.getRateLimit().isEnabled(),
            properties.getRateLimit().getMaxUploadsPerMinute(),
            properties.getRateLimit().getMaxUploadsPerHour()
        );
    }

    private QuotasConfig buildQuotas(String tenantId, String userId) {
        Integer tenantLimit = tenantId != null ? properties.getQuota().getTenant().get(tenantId) : null;
        Integer userLimit = userId != null ? properties.getQuota().getUser().get(userId) : null;
        return new QuotasConfig(
            properties.getQuota().isEnabled(),
            tenantLimit,
            userLimit
        );
    }

    // Messages are provided by ErrorMessageUtils.getAllMessages()
}
