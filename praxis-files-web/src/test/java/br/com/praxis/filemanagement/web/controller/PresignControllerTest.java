package br.com.praxis.filemanagement.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PresignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnPresignedUrl() throws Exception {
        mockMvc.perform(post("/api/files/upload/presign").param("filename", "test.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").exists());
    }
}
