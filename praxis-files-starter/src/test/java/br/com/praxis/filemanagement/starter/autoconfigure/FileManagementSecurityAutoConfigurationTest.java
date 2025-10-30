package br.com.praxis.filemanagement.starter.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = FileManagementSecurityAutoConfigurationTest.TestApplication.class,
    properties = {
        "file.management.security.permit-file-endpoints=true",
        "file.management.security.cors-enabled=true",
        "file.management.security.allowed-origins[0]=http://example.com"
    }
)
@AutoConfigureMockMvc
class FileManagementSecurityAutoConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitFileEndpointsAllowsAccess() throws Exception {
        mockMvc.perform(get("/api/files/test"))
            .andExpect(status().isOk());
    }

    @Test
    void corsConfigurationAppliesAllowedOrigins() throws Exception {
        mockMvc.perform(options("/api/files/test")
                .header("Origin", "http://example.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://example.com"));
    }

    @Configuration
    @Import(FileManagementSecurityAutoConfiguration.class)
    static class TestApplication {
        @RestController
        @RequestMapping("/api/files")
        static class TestController {
            @GetMapping("/test")
            String test() {
                return "ok";
            }
        }
    }
}
