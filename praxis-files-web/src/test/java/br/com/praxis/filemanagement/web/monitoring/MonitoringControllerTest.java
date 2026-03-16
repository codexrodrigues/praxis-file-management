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
            FileManagementProperties properties = new FileManagementProperties();
            properties.getMonitoring().setIncludeDetailedMetrics(false);
            return properties;
        }
    }

    @Autowired
    private MonitoringController controller;

    @Autowired
    private FileManagementProperties properties;

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
    @DisplayName("Should return 503 for degraded health")
    void shouldReturnDegradedHealthStatus() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setStatus(SystemMonitor.SystemStatus.DEGRADED);
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        ResponseEntity<SystemMonitor.SystemHealth> response =
            controller.getHealth(new MockHttpServletRequest());

        assertEquals(503, response.getStatusCodeValue());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return 500 when health check fails")
    void shouldReturnErrorHealthStatus() {
        Mockito.when(systemMonitor.getSystemHealth()).thenThrow(new RuntimeException("boom"));

        ResponseEntity<SystemMonitor.SystemHealth> response =
            controller.getHealth(new MockHttpServletRequest());

        assertEquals(500, response.getStatusCodeValue());
        assertEquals(SystemMonitor.SystemStatus.CRITICAL, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("boom"));
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should filter sensitive metrics when detailed metrics are disabled")
    void shouldFilterSensitiveMetrics() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setMetrics(Map.of(
            "configuration.maxFiles", 10,
            "configuration.uploadDir", "/tmp/uploads",
            "configuration.virusScanHost", "localhost",
            "security.blockedAttempts", 3,
            "security.summary", "hidden"
        ));
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        @SuppressWarnings("unchecked")
        ResponseEntity<Object> response = controller.getMetrics(new MockHttpServletRequest());
        Map<String, Object> body = (Map<String, Object>) response.getBody();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(10, body.get("configuration.maxFiles"));
        assertEquals(3, body.get("security.blockedAttempts"));
        assertFalse(body.containsKey("configuration.uploadDir"));
        assertFalse(body.containsKey("configuration.virusScanHost"));
        assertFalse(body.containsKey("security.summary"));
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return component status map")
    void shouldReturnStatusMap() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setComponents(Map.of("storage", "HEALTHY"));
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        ResponseEntity<Object> response = controller.getStatus();

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(Map.of("storage", "HEALTHY"), response.getBody());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return ping status for degraded system")
    void shouldReturnDegradedPing() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setStatus(SystemMonitor.SystemStatus.DEGRADED);
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        ResponseEntity<String> response = controller.ping();

        assertEquals(503, response.getStatusCodeValue());
        assertEquals("DEGRADED", response.getBody());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return critical ping when system is critical")
    void shouldReturnCriticalPing() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setStatus(SystemMonitor.SystemStatus.CRITICAL);
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        ResponseEntity<String> response = controller.ping();

        assertEquals(503, response.getStatusCodeValue());
        assertEquals("CRITICAL", response.getBody());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return error ping for null status")
    void shouldReturnUnknownPing() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);

        ResponseEntity<String> response = controller.ping();

        assertEquals(500, response.getStatusCodeValue());
        assertEquals("ERROR", response.getBody());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return status error payload when status lookup fails")
    void shouldReturnStatusErrorPayload() {
        Mockito.when(systemMonitor.getSystemHealth()).thenThrow(new RuntimeException("status-fail"));

        ResponseEntity<Object> response = controller.getStatus();

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(String.valueOf(response.getBody()).contains("status-fail"));
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return metrics error payload when metrics lookup fails")
    void shouldReturnMetricsErrorPayload() {
        Mockito.when(systemMonitor.getSystemHealth()).thenThrow(new RuntimeException("metrics-fail"));

        ResponseEntity<Object> response = controller.getMetrics(new MockHttpServletRequest());

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(String.valueOf(response.getBody()).contains("metrics-fail"));
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should preserve detailed metrics when enabled")
    void shouldPreserveDetailedMetricsWhenEnabled() {
        SystemMonitor.SystemHealth health = new SystemMonitor.SystemHealth();
        health.setMetrics(Map.of("configuration.uploadDir", "/tmp/uploads"));
        Mockito.when(systemMonitor.getSystemHealth()).thenReturn(health);
        properties.getMonitoring().setIncludeDetailedMetrics(true);

        ResponseEntity<Object> response = controller.getMetrics(new MockHttpServletRequest());

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(Map.of("configuration.uploadDir", "/tmp/uploads"), response.getBody());
        properties.getMonitoring().setIncludeDetailedMetrics(false);
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should return error ping when monitor fails")
    void shouldReturnErrorPing() {
        Mockito.when(systemMonitor.getSystemHealth()).thenThrow(new RuntimeException("boom"));

        ResponseEntity<String> response = controller.ping();

        assertEquals(500, response.getStatusCodeValue());
        assertEquals("ERROR", response.getBody());
    }

    @Test
    @WithMockUser(roles = "MONITORING")
    @DisplayName("Should expose version information")
    void shouldExposeVersionInfo() {
        ResponseEntity<Map<String, Object>> response = controller.getVersion();
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("success", response.getBody().get("status"));
        assertTrue(response.getBody().containsKey("message"));
        assertTrue(response.getBody().containsKey("data"));
        assertFalse(response.getBody().containsKey("success"));
        assertTrue(((Map<?, ?>) response.getBody().get("data")).containsKey("version"));
    }

    @Test
    @WithMockUser // no MONITORING role
    @DisplayName("Should deny access without required role")
    void shouldDenyAccessWithoutRole() {
        assertThrows(AccessDeniedException.class,
            () -> controller.getHealth(new MockHttpServletRequest()));
    }
}
