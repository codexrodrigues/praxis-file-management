package br.com.praxis.filemanagement.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigurationTest {

    @Test
    void shouldBuildExpectedOpenApiDocument() {
        OpenAPI openAPI = new OpenApiConfiguration().fileManagementOpenAPI();

        assertEquals("File Management Library API", openAPI.getInfo().getTitle());
        assertEquals("1.1.0-SNAPSHOT", openAPI.getInfo().getVersion());
        assertEquals(2, openAPI.getServers().size());
        assertEquals("basic", openAPI.getComponents().getSecuritySchemes().get("basicAuth").getScheme());
        assertTrue(openAPI.getInfo().getDescription().contains("Rate limiting"));
        assertNotNull(openAPI.getInfo().getLicense());
    }
}
