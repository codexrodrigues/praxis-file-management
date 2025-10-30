package br.com.praxis.filemanagement.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do OpenAPI/Swagger para documentação automática da API.
 * Configurado apenas quando o SpringDoc está no classpath.
 */
@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfiguration {

    /**
     * Configuração principal do OpenAPI/Swagger para documentação automática da API.
     * 
     * <p>Esta configuração define a documentação completa da API de gerenciamento
     * de arquivos, incluindo metadados, informações de contato, licenciamento
     * e configurações de segurança. A documentação é gerada automaticamente
     * e disponibilizada através do SpringDoc OpenAPI.
     * 
     * <p>Elementos configurados:
     * <ul>
     *   <li><strong>Informações da API</strong>: Título, descrição, versão e características</li>
     *   <li><strong>Contato</strong>: Informações da equipe de desenvolvimento</li>
     *   <li><strong>Licenciamento</strong>: MIT License com URL de referência</li>
     *   <li><strong>Servidores</strong>: URLs de desenvolvimento e produção</li>
     *   <li><strong>Segurança</strong>: Esquemas de autenticação HTTP Basic</li>
     * </ul>
     * 
     * <p>A documentação inclui:
     * <ul>
     *   <li>Descrição detalhada das características de segurança</li>
     *   <li>Diferenciação entre ambientes de desenvolvimento e produção</li>
     *   <li>Informações sobre validações automáticas e rate limiting</li>
     *   <li>Configuração de esquemas de autenticação para endpoints protegidos</li>
     * </ul>
     * 
     * <p>Esta configuração é ativada condicionalmente apenas quando a classe
     * {@code OpenAPI} está disponível no classpath (SpringDoc dependency).
     * 
     * <p>Acesso à documentação:
     * <ul>
     *   <li><strong>Swagger UI</strong>: {@code /swagger-ui.html}</li>
     *   <li><strong>OpenAPI JSON</strong>: {@code /v3/api-docs}</li>
     * </ul>
     * 
     * @return Instância configurada do OpenAPI com toda a documentação da API
     * @since 1.0.0
     */
    @Bean
    public OpenAPI fileManagementOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("File Management Library API")
                .description("""
                    API para upload e gerenciamento seguro de arquivos com validações automáticas.
                    
                    ## Características Principais
                    - ✅ Upload seguro com validação de tipo MIME
                    - ✅ Detecção de malware (ClamAV - opcional)  
                    - ✅ Validação de assinatura de arquivo
                    - ✅ Rate limiting configurável
                    - ✅ Auditoria completa de operações
                    - ✅ Respostas padronizadas em português
                    
                    ## Ambientes
                    - **Desenvolvimento**: Permissivo, debug habilitado
                    - **Produção**: Restritivo, autenticação obrigatória
                    
                    ## Segurança
                    Esta API implementa múltiplas camadas de segurança incluindo validação de tipos MIME,
                    verificação de assinatura de arquivo, escaneamento opcional de vírus e rate limiting.
                    """)
                .version("1.1.0-SNAPSHOT")
                .contact(new Contact()
                    .name("ErgonX Team")
                    .email("dev@ergonx.com.br")
                    .url("https://github.com/ergonx/file-management"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8086")
                    .description("Servidor de desenvolvimento"),
                new Server()
                    .url("https://api.example.com")
                    .description("Servidor de produção")))
            .components(new Components()
                .addSecuritySchemes("basicAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")
                    .description("Autenticação HTTP Basic. Credenciais: username:password em Base64")));
    }
}
