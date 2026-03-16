package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import br.com.praxis.filemanagement.web.service.FileEffectiveConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigControllerTest {

    private final FileEffectiveConfigService configService = Mockito.mock(FileEffectiveConfigService.class);
    private final ConfigController controller = new ConfigController(configService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void shouldReturnEffectiveConfig() throws Exception {
        EffectiveUploadConfigRecord config = new EffectiveUploadConfigRecord(
            FileUploadOptionsRecord.builder().nameConflictPolicy(NameConflictPolicy.RENAME).build(),
            new EffectiveUploadConfigRecord.BulkConfig(false, 50, 3),
            new EffectiveUploadConfigRecord.RateLimitConfig(true, 10, 100),
            new EffectiveUploadConfigRecord.QuotasConfig(false, null, null),
            Map.of(
                "FILE_TOO_LARGE", "Arquivo muito grande.",
                "RATE_LIMIT_EXCEEDED", "Limite excedido."
            ),
            new EffectiveUploadConfigRecord.Metadata("1.1.0", "pt-BR")
        );

        Mockito.when(configService.getEffectiveConfig(null, null)).thenReturn(config);

        mockMvc.perform(get("/api/files/config"))
            .andExpect(status().isOk())
            .andExpect(header().exists("ETag"))
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.success").doesNotExist())
            .andExpect(jsonPath("$.data.options.nameConflictPolicy").value("RENAME"))
            .andExpect(jsonPath("$.data.messages.FILE_TOO_LARGE").exists())
            .andExpect(jsonPath("$.data.messages.RATE_LIMIT_EXCEEDED").exists());
    }

    @Test
    void shouldForwardTenantAndUserHeadersToService() throws Exception {
        EffectiveUploadConfigRecord config = new EffectiveUploadConfigRecord(
            FileUploadOptionsRecord.builder().nameConflictPolicy(NameConflictPolicy.RENAME).build(),
            new EffectiveUploadConfigRecord.BulkConfig(false, 50, 3),
            new EffectiveUploadConfigRecord.RateLimitConfig(true, 10, 100),
            new EffectiveUploadConfigRecord.QuotasConfig(false, null, null),
            Map.of(),
            new EffectiveUploadConfigRecord.Metadata("1.1.0", "pt-BR")
        );

        Mockito.when(configService.getEffectiveConfig("tenant-a", "user-a")).thenReturn(config);

        mockMvc.perform(
                get("/api/files/config")
                    .header("X-Tenant-Id", "tenant-a")
                    .header("X-User-Id", "user-a")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=60")));

        Mockito.verify(configService).getEffectiveConfig("tenant-a", "user-a");
    }
}
