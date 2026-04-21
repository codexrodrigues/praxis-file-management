package br.com.praxis.filemanagement.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("noauth")
class NoAuthSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unknownRouteIsNotChallengedBySecurity() throws Exception {
        mockMvc.perform(get("/route-that-does-not-exist"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                assertNotEquals(401, status);
                assertNotEquals(403, status);
            });
    }

    @Test
    void actuatorHealthRemainsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void filesConfigEndpointRemainsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/files/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.data").exists())
            .andExpect(jsonPath("$.data.options").exists());
    }

    @Test
    void singleUploadEndpointAcceptsAnonymousMultipartRequestsInNoAuthProfile() throws Exception {
        String fileName = "noauth-upload-" + System.nanoTime() + ".txt";
        MockMultipartFile file = new MockMultipartFile(
            "file",
            fileName,
            "text/plain",
            "noauth upload".getBytes()
        );

        MvcResult result = mockMvc.perform(
                multipart("/api/files/upload")
                    .file(file)
                    .param("options", "{\"nameConflictPolicy\":\"RENAME\",\"maxUploadSizeMb\":5}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.data").exists())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            new TypeReference<Map<String, Object>>() {}
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        String serverFilename = String.valueOf(data.get("serverFilename"));

        Files.deleteIfExists(Path.of("uploads", serverFilename));
    }
}
