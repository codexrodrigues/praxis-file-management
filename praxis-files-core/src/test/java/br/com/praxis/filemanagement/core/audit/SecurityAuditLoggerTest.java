package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SecurityAuditLoggerTest {

    @Mock
    private SecurityFailureRegistry securityFailureRegistry;

    private FileManagementProperties properties;
    private SecurityAuditLogger auditLogger;

    @BeforeEach
    void setUp() throws Exception {
        properties = new FileManagementProperties();
        auditLogger = new SecurityAuditLogger();

        Field propsField = SecurityAuditLogger.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        propsField.set(auditLogger, properties);

        Field registryField = SecurityAuditLogger.class.getDeclaredField("securityFailureRegistry");
        registryField.setAccessible(true);
        registryField.set(auditLogger, securityFailureRegistry);
    }

    @Test
    void skipsLoggingWhenDisabled() {
        FileManagementProperties.AuditLogging audit = new FileManagementProperties.AuditLogging();
        audit.setEnabled(false);
        properties.setAuditLogging(audit);

        auditLogger.logSecurityViolation("1.1.1.1", "file.txt", 10L, "text/plain",
                FileErrorReason.MIME_TYPE_MISMATCH, "err", "details");

        verifyNoInteractions(securityFailureRegistry);
    }

    @Test
    void logsWhenEnabled() {
        FileManagementProperties.AuditLogging audit = new FileManagementProperties.AuditLogging();
        audit.setEnabled(true);
        properties.setAuditLogging(audit);

        auditLogger.logSecurityViolation("1.1.1.1", "file.txt", 10L, "text/plain",
                FileErrorReason.MIME_TYPE_MISMATCH, "err", "details");

        verify(securityFailureRegistry).registerSecurityFailure(any(), any(), any(), any(), any(), any(), any());
    }
}
