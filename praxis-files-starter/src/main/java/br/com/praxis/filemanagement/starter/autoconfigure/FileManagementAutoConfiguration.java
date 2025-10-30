package br.com.praxis.filemanagement.starter.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import br.com.praxis.filemanagement.starter.autoconfigure.FileManagementSecurityProperties;

@AutoConfiguration
@EnableConfigurationProperties(FileManagementSecurityProperties.class)
@Import(CoreBeansConfiguration.class)
public class FileManagementAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "praxis.files.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import(WebBeansConfiguration.class)
    static class WebLayerAutoImport {
    }
}
