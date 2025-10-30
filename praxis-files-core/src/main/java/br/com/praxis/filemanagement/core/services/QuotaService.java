package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory quota tracking per tenant and user.
 */
@Service
public class QuotaService {

    private final FileManagementProperties properties;
    private final Map<String, AtomicInteger> tenantUsage = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> userUsage = new ConcurrentHashMap<>();

    public QuotaService(FileManagementProperties properties) {
        this.properties = properties;
    }

    public boolean isTenantQuotaExceeded(String tenantId) {
        if (!properties.getQuota().isEnabled() || tenantId == null) {
            return false;
        }
        Integer limit = properties.getQuota().getTenant().get(tenantId);
        if (limit == null) {
            return false;
        }
        AtomicInteger usage = tenantUsage.get(tenantId);
        return usage != null && usage.get() >= limit;
    }

    public boolean isUserQuotaExceeded(String userId) {
        if (!properties.getQuota().isEnabled() || userId == null) {
            return false;
        }
        Integer limit = properties.getQuota().getUser().get(userId);
        if (limit == null) {
            return false;
        }
        AtomicInteger usage = userUsage.get(userId);
        return usage != null && usage.get() >= limit;
    }

    public void recordTenantUpload(String tenantId) {
        if (!properties.getQuota().isEnabled() || tenantId == null) {
            return;
        }
        Integer limit = properties.getQuota().getTenant().get(tenantId);
        if (limit == null) {
            return;
        }
        tenantUsage.computeIfAbsent(tenantId, k -> new AtomicInteger()).incrementAndGet();
    }

    public void recordUserUpload(String userId) {
        if (!properties.getQuota().isEnabled() || userId == null) {
            return;
        }
        Integer limit = properties.getQuota().getUser().get(userId);
        if (limit == null) {
            return;
        }
        userUsage.computeIfAbsent(userId, k -> new AtomicInteger()).incrementAndGet();
    }
}
