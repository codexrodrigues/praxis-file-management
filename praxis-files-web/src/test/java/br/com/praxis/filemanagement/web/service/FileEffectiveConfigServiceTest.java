package br.com.praxis.filemanagement.web.service;

import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileEffectiveConfigServiceTest {

    @Test
    void shouldBuildConfigFromProperties() {
        FileManagementProperties props = new FileManagementProperties();
        props.setMaxFileSizeBytes(50 * 1024 * 1024L);
        props.getSecurity().setStrictValidation(true);
        props.getBulkUpload().setFailFastMode(true);

        FileEffectiveConfigService service = new FileEffectiveConfigService(props);
        EffectiveUploadConfigRecord record = service.getEffectiveConfig(null, null);

        assertEquals(50L, record.options().maxUploadSizeMb());
        assertTrue(record.options().strictValidation());
        assertTrue(record.bulk().failFastModeDefault());
        assertNotNull(record.messages().get("FILE_TOO_LARGE"));
        assertNotNull(record.messages().get("RATE_LIMIT_EXCEEDED"));
    }
}
