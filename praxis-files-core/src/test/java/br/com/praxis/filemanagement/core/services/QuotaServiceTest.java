package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaServiceTest {

    @Test
    @DisplayName("ignores quota checks when feature is disabled")
    void ignoresQuotaChecksWhenFeatureIsDisabled() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getQuota().setEnabled(false);
        properties.getQuota().getTenant().put("tenant-a", 1);
        properties.getQuota().getUser().put("user-a", 1);

        QuotaService service = new QuotaService(properties);

        service.recordTenantUpload("tenant-a");
        service.recordUserUpload("user-a");

        assertFalse(service.isTenantQuotaExceeded("tenant-a"));
        assertFalse(service.isUserQuotaExceeded("user-a"));
    }

    @Test
    @DisplayName("tracks tenant quota only for configured tenants")
    void tracksTenantQuotaOnlyForConfiguredTenants() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getQuota().setEnabled(true);
        properties.getQuota().getTenant().put("tenant-a", 2);

        QuotaService service = new QuotaService(properties);

        assertFalse(service.isTenantQuotaExceeded("tenant-a"));
        service.recordTenantUpload("tenant-a");
        assertFalse(service.isTenantQuotaExceeded("tenant-a"));
        service.recordTenantUpload("tenant-a");
        assertTrue(service.isTenantQuotaExceeded("tenant-a"));

        service.recordTenantUpload("unknown");
        assertFalse(service.isTenantQuotaExceeded("unknown"));
        assertFalse(service.isTenantQuotaExceeded(null));
    }

    @Test
    @DisplayName("tracks user quota only for configured users")
    void tracksUserQuotaOnlyForConfiguredUsers() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getQuota().setEnabled(true);
        properties.getQuota().getUser().put("user-a", 1);

        QuotaService service = new QuotaService(properties);

        assertFalse(service.isUserQuotaExceeded("user-a"));
        service.recordUserUpload("user-a");
        assertTrue(service.isUserQuotaExceeded("user-a"));

        service.recordUserUpload("unknown");
        assertFalse(service.isUserQuotaExceeded("unknown"));
        assertFalse(service.isUserQuotaExceeded(null));
    }
}
