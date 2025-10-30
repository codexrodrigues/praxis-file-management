package br.com.praxis.filemanagement.app;

import br.com.praxis.filemanagement.web.controller.FileController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "praxis.files.web.enabled=false")
class WebDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void webControllerIsNotLoaded() {
        Assertions.assertThrows(NoSuchBeanDefinitionException.class,
            () -> applicationContext.getBean(FileController.class));
    }
}
