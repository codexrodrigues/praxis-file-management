package br.com.praxis.filemanagement.starter.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuração de segurança para a biblioteca de gerenciamento de arquivos
 * Fornece configuração padrão flexível que pode ser sobrescrita
 *
 * @author ErgonX
 * @since 1.0.0
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(FileManagementSecurityProperties.class)
public class FileManagementSecurityAutoConfiguration {

    private final FileManagementSecurityProperties properties;

    public FileManagementSecurityAutoConfiguration(FileManagementSecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * Configuração de segurança padrão para a biblioteca de gerenciamento de arquivos.
     *
     * <p>Esta configuração é aplicada automaticamente apenas quando o usuário não
     * definir uma configuração de segurança customizada (Spring Boot auto-configuration).
     * Fornece uma configuração base flexível que pode ser completamente sobrescrita.
     *
     * <p>Configurações aplicadas:
     * <ul>
     *   <li><strong>CSRF</strong>: Configurável via propriedades, desabilitado para uploads</li>
     *   <li><strong>CORS</strong>: Configurável com origens, métodos e headers customizáveis</li>
     *   <li><strong>Autorização</strong>: Padrões flexíveis para endpoints de arquivo e saúde</li>
     *   <li><strong>Handlers de erro</strong>: Respostas padronizadas em JSON com códigos apropriados</li>
     * </ul>
     *
     * <p>Ordem de precedência para autorização:
     * <ol>
     *   <li>Padrões protegidos (sempre requerem autenticação)</li>
     *   <li>Endpoints específicos (arquivos, actuator, health)</li>
     *   <li>Padrões permitidos customizados</li>
     *   <li>Configuração padrão (autenticação obrigatória ou permitir tudo)</li>
     * </ol>
     *
     * <p>Esta configuração inclui correção de segurança que restringe endpoints
     * de health apenas a {@code /file-management/health} e {@code /file-management/ping},
     * evitando exposição acidental de outros endpoints administrativos.
     *
     * @param http Configurador de segurança HTTP do Spring Security
     * @return SecurityFilterChain configurado com as regras de segurança da biblioteca
     * @throws Exception Se houver erro na configuração de segurança
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain defaultFileManagementSecurityFilterChain(HttpSecurity http,
                                                                        CorsConfigurationSource corsConfigurationSource) throws Exception {
        // Configuração de CSRF
        if (properties.isCsrfEnabled()) {
            http.csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/files/upload/**") // Permitir upload sem CSRF
            );
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        // Configuração de CORS
        if (properties.isCorsEnabled()) {
            http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        }

        // Configuração de autorização
        http.authorizeHttpRequests(auth -> {
            // Primeiro aplica padrões protegidos (tem precedência)
            for (String pattern : properties.getProtectedPatterns()) {
                auth.requestMatchers(pattern).authenticated();
            }

            // Configura endpoints permitidos baseado nas propriedades
            if (properties.isPermitFileEndpoints()) {
                auth.requestMatchers("/api/files/**").permitAll();
            }
            if (properties.isPermitActuatorEndpoints()) {
                auth.requestMatchers("/actuator/**").permitAll();
            }
            if (properties.isPermitHealthEndpoints()) {
                // CORREÇÃO DE SEGURANÇA: Permitir apenas endpoints de health específicos
                auth.requestMatchers("/file-management/health", "/file-management/ping").permitAll();
            }

            // Aplica padrões permitidos customizados
            for (String pattern : properties.getPermittedPatterns()) {
                auth.requestMatchers(pattern).permitAll();
            }

            // Resto requer autenticação se habilitado
            if (properties.isRequireAuthenticationByDefault()) {
                auth.anyRequest().authenticated();
            } else {
                auth.anyRequest().permitAll();
            }
        });

        // Configuração de handlers de autenticação/autorização
        http.exceptionHandling(exceptions -> exceptions
            .authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "AUTHENTICATION_REQUIRED");
                errorResponse.put("message", "Autenticação necessária para acessar este recurso");
                errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());
                errorResponse.put("timestamp", Instant.now().toString());
                errorResponse.put("path", request.getRequestURI());

                ObjectMapper mapper = new ObjectMapper();
                response.getWriter().write(mapper.writeValueAsString(errorResponse));
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "ACCESS_DENIED");
                errorResponse.put("message", "Acesso negado - você não tem permissão para acessar este recurso");
                errorResponse.put("status", HttpStatus.FORBIDDEN.value());
                errorResponse.put("timestamp", Instant.now().toString());
                errorResponse.put("path", request.getRequestURI());

                ObjectMapper mapper = new ObjectMapper();
                response.getWriter().write(mapper.writeValueAsString(errorResponse));
            })
        );

        return http.build();
    }

    /**
     * Configuração de CORS (Cross-Origin Resource Sharing) baseada nas propriedades da biblioteca.
     *
     * <p>Esta configuração é aplicada automaticamente apenas quando não existe
     * uma configuração CORS customizada definida pelo usuário. Permite configuração
     * flexível de políticas CORS através das propriedades da aplicação.
     *
     * <p>Configurações CORS aplicadas:
     * <ul>
     *   <li><strong>Origens permitidas</strong>: Configurável via propriedades ou wildcard (*)</li>
     *   <li><strong>Métodos permitidos</strong>: GET, POST, PUT, DELETE, OPTIONS</li>
     *   <li><strong>Headers permitidos</strong>: Todos (*) para flexibilidade máxima</li>
     *   <li><strong>Credenciais</strong>: Permitidas apenas se origens específicas forem definidas</li>
     * </ul>
     *
     * <p>Comportamento de segurança:
     * <ul>
     *   <li>Se nenhuma origem for configurada, aplica wildcard (*)</li>
     *   <li>Credenciais são automaticamente desabilitadas com wildcard por segurança</li>
     *   <li>Configuração se aplica a todos os endpoints (/**)</li>
     * </ul>
     *
     * <p>Esta configuração é essencial para permitir que aplicações frontend
     * (React, Angular, Vue.js) acessem a API de gerenciamento de arquivos a partir
     * de diferentes domínios durante desenvolvimento e produção.
     *
     * @return CorsConfigurationSource configurado com as políticas CORS da biblioteca
     * @since 1.0.0
     */
    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Configura origens permitidas
        if (properties.getAllowedOrigins().isEmpty()) {
            configuration.addAllowedOrigin("*");
        } else {
            configuration.setAllowedOrigins(properties.getAllowedOrigins());
        }

        // Métodos permitidos
        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("PUT");
        configuration.addAllowedMethod("DELETE");
        configuration.addAllowedMethod("OPTIONS");

        // Headers permitidos
        configuration.addAllowedHeader("*");

        // Permite credenciais se não for origem wildcard
        configuration.setAllowCredentials(!properties.getAllowedOrigins().isEmpty() &&
                                        !properties.getAllowedOrigins().contains("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
