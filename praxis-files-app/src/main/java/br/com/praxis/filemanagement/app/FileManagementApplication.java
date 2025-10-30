package br.com.praxis.filemanagement.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Aplicação de exemplo que consome a biblioteca de gerenciamento de arquivos
 * Demonstra como usar a biblioteca com configuração mínima
 * 
 * @author ErgonX
 * @since 1.0.0
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "br.com.praxis.filemanagement.app",
    "br.com.praxis.filemanagement.core",
    "br.com.praxis.filemanagement.starter"
})
public class FileManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileManagementApplication.class, args);
    }
}