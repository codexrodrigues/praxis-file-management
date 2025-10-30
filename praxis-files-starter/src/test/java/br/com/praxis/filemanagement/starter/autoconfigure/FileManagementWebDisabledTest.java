package br.com.praxis.filemanagement.starter.autoconfigure;

import br.com.praxis.filemanagement.core.services.LocalStorageFileService;
import br.com.praxis.filemanagement.web.controller.FileController;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FileManagementWebDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FileManagementAutoConfiguration.class))
        .withPropertyValues("praxis.files.web.enabled=false");

    @Test
    void webLayerBeansAreNotLoadedWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LocalStorageFileService.class);
            assertThat(context).doesNotHaveBean(FileController.class);
            assertThat(context).doesNotHaveBean(OpenAPI.class);
        });
    }
}
