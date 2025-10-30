package br.com.praxis.filemanagement.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "file.management.quota.enabled=true",
        "file.management.quota.tenant.test-tenant=1"
})
class FileControllerQuotaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn429WhenQuotaExceeded() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file).header("X-Tenant-Id", "test-tenant"))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/files/upload").file(file).header("X-Tenant-Id", "test-tenant"))
                .andExpect(status().isTooManyRequests());
    }
}
