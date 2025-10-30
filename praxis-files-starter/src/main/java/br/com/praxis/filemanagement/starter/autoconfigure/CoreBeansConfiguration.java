package br.com.praxis.filemanagement.starter.autoconfigure;

import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.audit.SecurityAuditLogger;
import br.com.praxis.filemanagement.core.audit.SecurityFailureRegistry;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.monitoring.SystemMonitor;
import br.com.praxis.filemanagement.core.services.LocalStorageFileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileManagementProperties.class)
@ComponentScan(basePackages = "br.com.praxis.filemanagement.core")
public class CoreBeansConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FileService fileService() {
        return new LocalStorageFileService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityAuditLogger securityAuditLogger() {
        return new SecurityAuditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public UploadAttemptTracker uploadAttemptTracker() {
        return new UploadAttemptTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityFailureRegistry securityFailureRegistry() {
        return new SecurityFailureRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemMonitor systemMonitor() {
        return new SystemMonitor();
    }
}
