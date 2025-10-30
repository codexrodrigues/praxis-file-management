package br.com.praxis.filemanagement.starter.autoconfigure;

import br.com.praxis.filemanagement.web.config.OpenApiConfiguration;
import br.com.praxis.filemanagement.web.controller.FileController;
import br.com.praxis.filemanagement.web.controller.ConfigController;
import br.com.praxis.filemanagement.web.error.GlobalExceptionHandler;
import br.com.praxis.filemanagement.web.monitoring.MonitoringController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;

@Configuration
@Import({FileController.class, ConfigController.class, GlobalExceptionHandler.class, MonitoringController.class, OpenApiConfiguration.class})
@ComponentScan(basePackages = {
    "br.com.praxis.filemanagement.web.service",
    "br.com.praxis.filemanagement.web.filter"
})
public class WebBeansConfiguration {
}
