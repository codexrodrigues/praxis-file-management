package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.services.PresignedUrlService;
import br.com.praxis.filemanagement.core.services.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PresignControllerTest {

    private final FileService fileService = Mockito.mock(FileService.class);
    private final QuotaService quotaService = Mockito.mock(QuotaService.class);
    private final PresignedUrlService presignedUrlService = Mockito.mock(PresignedUrlService.class);
    private final FileController controller = new FileController(fileService, new ObjectMapper(), quotaService, presignedUrlService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void shouldReturnPresignedUrl() throws Exception {
        Mockito.when(presignedUrlService.createUploadUrl("test.txt"))
            .thenReturn("https://upload.example/test.txt");

        mockMvc.perform(post("/api/files/upload/presign").param("filename", "test.txt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl").value("https://upload.example/test.txt"))
            .andExpect(jsonPath("$.headers").isMap())
            .andExpect(jsonPath("$.fields").isMap())
            .andExpect(jsonPath("$.status").doesNotExist());
    }
}
