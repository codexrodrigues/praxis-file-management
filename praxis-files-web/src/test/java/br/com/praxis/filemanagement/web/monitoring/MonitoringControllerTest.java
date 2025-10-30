package br.com.praxis.filemanagement.web.monitoring;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.monitoring.SystemMonitor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnableMethodSecurity
@ContextConfiguration(classes = {MonitoringController.class, MonitoringControllerTest.TestConfig.class})
public class MonitoringControllerTest {

    @Configuration
    static class TestConfig {
        @Bean
        FileManagementProperties fileManagementProperties() {
            return new FileManagementProperties();
        }
    }

    @Autowired
    private MonitoringController controller;

    @MockBean
    private SystemMonitor systemMonitor;

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return HEALTHY status")
    void shouldReturnHealthStatus() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setStatus(SystemMonitor.SystemStatus.HEALTHY);
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        ResponseEntity<SystemMonitor.SystemHealth> response =
            controller.getHealth(new MockHttpServletRequest());

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(health, response.getBody());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should expose version information")
    void shouldExposeVersionInfo() {
        ResponseEntity<Map<String, Object>> response = controller.getVersion();
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().containsKey("version"));
    }

    @Test
    @WithMockUser // no MONITORING role
    @DisplayName("Should deny access without required role")
    void shouldDenyAccessWithoutRole() {
        assertThrows(AccessDeniedException.class,
            () -> controller.getHealth(new MockHttpServletRequest()));
    }
}
