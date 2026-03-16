package br.com.praxis.filemanagement.web.error;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.exception.FileSizeLimitExceededException;
import br.com.praxis.filemanagement.core.exception.RateLimitExceededException;
import br.com.praxis.filemanagement.core.exception.QuotaExceededException;
import br.com.praxis.filemanagement.core.utils.ErrorMessageUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.Map;

// imports adicionais
import br.com.praxis.filemanagement.core.services.RateLimitingService;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.web.filter.RemoteIpResolver;

/**
 * Manipulador global de exceções para a biblioteca de gerenciamento de arquivos.
 *
 * <p>Centraliza o tratamento de todas as exceções da aplicação, garantindo:
 * <ul>
 *   <li><strong>Respostas padronizadas</strong>: Formato consistente de erro em toda a API</li>
 *   <li><strong>Códigos HTTP apropriados</strong>: Status codes adequados para cada tipo de erro</li>
 *   <li><strong>Logging estruturado</strong>: Registro adequado de erros para monitoramento</li>
 *   <li><strong>Mensagens user-friendly</strong>: Mensagens em português com detalhes úteis</li>
 *   <li><strong>Segurança</strong>: Ocultação de detalhes internos sensíveis</li>
 * </ul>
 *
 * <p>Todos os erros são formatados usando {@link ErrorMessageUtils} para garantir
 * consistência no formato de resposta da API.
 *
 * <p>Este handler intercepta exceções de todos os controllers da aplicação
 * através da anotação {@code @RestControllerAdvice}.
 *
 * @author ErgonX
 * @since 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final FileErrorMetricsService errorMetrics;
    // dependências opcionais para headers de rate limit
    private final RateLimitingService rateLimitingService;
    private final FileManagementProperties properties;
    private final RemoteIpResolver remoteIpResolver;

    // Default constructor for Spring
    public GlobalExceptionHandler() {
        this.errorMetrics = null;
        this.rateLimitingService = null;
        this.properties = null;
        this.remoteIpResolver = null;
    }

    public GlobalExceptionHandler(ObjectProvider<FileErrorMetricsService> errorMetricsProvider) {
        this.errorMetrics = errorMetricsProvider.getIfAvailable();
        this.rateLimitingService = null;
        this.properties = null;
        this.remoteIpResolver = null;
    }

    // Constructor for tests or direct injection
    public GlobalExceptionHandler(FileErrorMetricsService errorMetrics) {
        this.errorMetrics = errorMetrics;
        this.rateLimitingService = null;
        this.properties = null;
        this.remoteIpResolver = null;
    }

    // Novo construtor opcional para injetar serviços de rate limit
    public GlobalExceptionHandler(ObjectProvider<FileErrorMetricsService> errorMetricsProvider,
                                  ObjectProvider<RateLimitingService> rateLimitingServiceProvider,
                                  ObjectProvider<FileManagementProperties> propertiesProvider,
                                  ObjectProvider<RemoteIpResolver> remoteIpResolverProvider) {
        this.errorMetrics = errorMetricsProvider.getIfAvailable();
        this.rateLimitingService = rateLimitingServiceProvider.getIfAvailable();
        this.properties = propertiesProvider.getIfAvailable();
        this.remoteIpResolver = remoteIpResolverProvider.getIfAvailable();
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, FileErrorReason reason,
                                                         Map<String, Object> map, HttpServletRequest request) {
        if (errorMetrics != null) {
            String endpoint = request != null ? request.getRequestURI() : "unknown";
            errorMetrics.increment(reason, endpoint);
        }
        ErrorResponse response = ErrorResponse.fromMap(map).withTraceId(getTraceId());
        return ResponseEntity.status(status).body(response);
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : MDC.get("trace_id");
    }

    /**
     * Manipula exceções quando campos obrigatórios estão ausentes em requisições multipart.
     *
     * <p>Esta exceção ocorre quando:
     * <ul>
     *   <li>O campo "file" não é fornecido em requisições de upload</li>
     *   <li>Campos obrigatórios de formulário multipart estão ausentes</li>
     *   <li>Nome incorreto é usado para o campo de arquivo</li>
     * </ul>
     *
     * <p>Resposta gerada:
     * <ul>
     *   <li><strong>HTTP Status</strong>: 400 (Bad Request)</li>
     *   <li><strong>Código de Erro</strong>: CAMPO_OBRIGATORIO_AUSENTE</li>
     *   <li><strong>Mensagem</strong>: Identifica o campo específico ausente</li>
     *   <li><strong>Detalhes</strong>: Orientação sobre o nome correto do campo</li>
     * </ul>
     *
     * @param ex Exceção contendo informações sobre o campo ausente
     * @return ResponseEntity com detalhes do erro e HTTP 400
     * @since 1.0.0
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        logger.warn("Missing required part in multipart request: {}", ex.getRequestPartName());

        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "CAMPO_OBRIGATORIO_AUSENTE",
            "O campo '" + ex.getRequestPartName() + "' é obrigatório",
            "Certifique-se de que o arquivo está sendo enviado com o nome correto do campo"
        );

        return buildResponse(HttpStatus.BAD_REQUEST, FileErrorReason.UNKNOWN_ERROR, map, request);
    }

    /**
     * Manipula exceções quando o tamanho do arquivo excede o limite configurado.
     *
     * <p>Esta exceção é lançada pelo Spring quando:
     * <ul>
     *   <li>Arquivo individual excede {@code spring.servlet.multipart.max-file-size}</li>
     *   <li>Requisição total excede {@code spring.servlet.multipart.max-request-size}</li>
     *   <li>Limite do container web é ultrapassado</li>
     * </ul>
     *
     * <p>Esta é uma validação de nível de infraestrutura que ocorre antes
     * da validação de negócio implementada nos serviços.
     *
     * <p>Resposta gerada:
     * <ul>
     *   <li><strong>HTTP Status</strong>: 413 (Payload Too Large)</li>
     *   <li><strong>Código de Erro</strong>: ARQUIVO_MUITO_GRANDE</li>
     *   <li><strong>Mensagem</strong>: Indica que limite foi excedido</li>
     *   <li><strong>Detalhes</strong>: Orientação sobre limite do sistema</li>
     * </ul>
     *
     * @param ex Exceção contendo detalhes sobre o tamanho excedido
     * @return ResponseEntity com detalhes do erro e HTTP 413
     * @since 1.0.0
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        logger.warn("Upload size exceeded: {}", ex.getMessage());

        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "ARQUIVO_MUITO_GRANDE",
            "Arquivo excede o tamanho máximo permitido",
            "O arquivo enviado é maior que o limite configurado no sistema"
        );

        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, FileErrorReason.FILE_TOO_LARGE, map, request);
    }

    /**
     * Handles business-level file size violations detected by the service layer.
     *
     * <p>Maps to HTTP 413 with a descriptive message including the configured limit.</p>
     */
    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileSizeLimitExceeded(FileSizeLimitExceededException ex, HttpServletRequest request) {
        logger.warn("File size {} exceeds limit {}", ex.getFileSize(), ex.getMaxAllowedSize());

        String details = "O arquivo enviado possui " + ex.getFileSize() +
                " bytes e excede o limite de " + ex.getMaxAllowedSize() + " bytes";

        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "ARQUIVO_MUITO_GRANDE",
            "Arquivo excede o tamanho máximo permitido",
            details
        );

        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, FileErrorReason.FILE_TOO_LARGE, map, request);
    }

    /**
     * Manipula exceções de parsing JSON nas opções de upload.
     *
     * <p>Esta exceção ocorre quando:
     * <ul>
     *   <li>JSON do parâmetro "options" tem sintaxe inválida</li>
     *   <li>Estrutura JSON não corresponde ao {@code FileUploadOptionsRecord}</li>
     *   <li>Valores de campos têm tipos incompatíveis</li>
     *   <li>Campos obrigatórios têm valores null quando não permitido</li>
     * </ul>
     *
     * <p>Exemplos de JSON inválido:
     * <ul>
     *   <li>{"maxUploadSizeMb": "não-numérico"}</li>
     *   <li>{"nameConflictPolicy": "VALOR_INEXISTENTE"}</li>
     *   <li>{"allowedExtensions": "deveria-ser-array"}</li>
     * </ul>
     *
     * <p>Resposta gerada:
     * <ul>
     *   <li><strong>HTTP Status</strong>: 400 (Bad Request)</li>
     *   <li><strong>Código de Erro</strong>: OPCOES_JSON_INVALIDAS</li>
     *   <li><strong>Mensagem</strong>: Indica erro no formato JSON</li>
     *   <li><strong>Detalhes</strong>: Mensagem técnica específica do parsing</li>
     * </ul>
     *
     * @param ex Exceção contendo detalhes específicos do erro de parsing
     * @return ResponseEntity com detalhes do erro e HTTP 400
     * @since 1.0.0
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonProcessingException(JsonProcessingException ex, HttpServletRequest request) {
        logger.warn("JSON processing error: {}", ex.getMessage());

        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "OPCOES_JSON_INVALIDAS",
            "Formato JSON inválido para opções de upload",
            "Verifique a sintaxe do JSON enviado: " + ex.getMessage()
        );

        return buildResponse(HttpStatus.BAD_REQUEST, FileErrorReason.UNKNOWN_ERROR, map, request);
    }

    /**
     * Manipula exceções de argumentos ilegais em validação de entrada.
     *
     * <p>Esta exceção é lançada pelos serviços quando:
     * <ul>
     *   <li>Parâmetros de entrada têm valores inválidos</li>
     *   <li>Configurações conflitantes são detectadas</li>
     *   <li>Valores estão fora de faixas permitidas</li>
     *   <li>Formatos de dados são incorretos</li>
     * </ul>
     *
     * <p>Exemplos de cenários que geram esta exceção:
     * <ul>
     *   <li>maxUploadSizeMb com valor negativo</li>
     *   <li>targetDirectory com caracteres inválidos</li>
     *   <li>allowedExtensions vazio quando obrigatório</li>
     *   <li>nameConflictPolicy inconsistente com outras opções</li>
     * </ul>
     *
     * <p>Resposta gerada:
     * <ul>
     *   <li><strong>HTTP Status</strong>: 400 (Bad Request)</li>
     *   <li><strong>Código de Erro</strong>: ARGUMENTO_INVALIDO</li>
     *   <li><strong>Mensagem</strong>: Indica problema com argumento</li>
     *   <li><strong>Detalhes</strong>: Mensagem específica do serviço sobre o problema</li>
     * </ul>
     *
     * @param ex Exceção contendo mensagem específica sobre o argumento inválido
     * @return ResponseEntity com detalhes do erro e HTTP 400
     * @since 1.0.0
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        logger.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "ARGUMENTO_INVALIDO",
            "Argumento inválido fornecido",
            ex.getMessage()
        );

        return buildResponse(HttpStatus.BAD_REQUEST, FileErrorReason.INVALID_TYPE, map, request);
    }

    /**
     * Handles authentication failures returning HTTP 401.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        logger.warn("Unauthorized access attempt: {}", ex.getMessage());
        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "NAO_AUTORIZADO",
            "Autenticação necessária",
            "Credenciais ausentes ou inválidas"
        );
        return buildResponse(HttpStatus.UNAUTHORIZED, FileErrorReason.SECURITY_VIOLATION, map, request);
    }

    /**
     * Maps unsupported media types to HTTP 415 responses.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        logger.warn("Unsupported media type: {}", ex.getMessage());
        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "TIPO_MIDIA_NAO_SUPORTADO",
            "Tipo de mídia não suportado",
            "Verifique o Content-Type da requisição"
        );
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, FileErrorReason.INVALID_TYPE, map, request);
    }

    /**
     * Handles rate limiting violations with HTTP 429.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        logger.warn("Rate limit exceeded: {}", ex.getMessage());
        Map<String, Object> map =
            ErrorMessageUtils.createGenericErrorResponseByKey("RATE_LIMIT_EXCEEDED", null, null);

        // Tentar adicionar headers X-RateLimit-*
        if (properties != null && rateLimitingService != null && remoteIpResolver != null && request != null) {
            try {
                String ip = remoteIpResolver.resolve(request);
                long remaining = Math.max(0, rateLimitingService.getRemainingUploadsPerMinute(ip));
                int limit = properties.getRateLimit().getMaxUploadsPerMinute();
                long reset = 60; // segundos até próximo refill (aproximação)

                HttpHeaders headers = new HttpHeaders();
                headers.add("X-RateLimit-Limit", String.valueOf(limit));
                headers.add("X-RateLimit-Remaining", String.valueOf(remaining));
                headers.add("X-RateLimit-Reset", String.valueOf(reset));

                if (errorMetrics != null) {
                    String endpoint = request.getRequestURI();
                    errorMetrics.increment(FileErrorReason.RATE_LIMIT_EXCEEDED, endpoint);
                }
                ErrorResponse response = ErrorResponse.fromMap(map).withTraceId(getTraceId());
                return new ResponseEntity<>(response, headers, HttpStatus.TOO_MANY_REQUESTS);
            } catch (Exception e) {
                logger.debug("Failed to add X-RateLimit headers: {}", e.getMessage());
            }
        }

        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, FileErrorReason.RATE_LIMIT_EXCEEDED, map, request);
    }

    /**
     * Handles quota violations with HTTP 429.
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuotaExceeded(QuotaExceededException ex, HttpServletRequest request) {
        logger.warn("Quota exceeded: {}", ex.getMessage());
        Map<String, Object> map =
            ErrorMessageUtils.createGenericErrorResponseByKey("QUOTA_EXCEEDED", null, null);
        // Sem headers específicos de rate limit para quota, apenas retorna 429
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, FileErrorReason.QUOTA_EXCEEDED, map, request);
    }

    /**
     * Manipula todas as exceções não tratadas pelos handlers específicos.
     *
     * <p>Este é o handler de último recurso para:
     * <ul>
     *   <li><strong>Erros não antecipados</strong>: Exceções não mapeadas especificamente</li>
     *   <li><strong>Falhas de sistema</strong>: Problemas de infraestrutura ou dependências</li>
     *   <li><strong>Bugs de código</strong>: NullPointerException, ClassCastException, etc.</li>
     *   <li><strong>Problemas de recursos</strong>: OutOfMemoryError, IOException, etc.</li>
     * </ul>
     *
     * <p><strong>Comportamento de segurança</strong>:
     * <ul>
     *   <li>Detalhes técnicos NÃO são expostos ao cliente</li>
     *   <li>Stack trace completo é logado para debugging</li>
     *   <li>Mensagem genérica é retornada para o usuário</li>
     *   <li>Erro é marcado como CRITICAL para monitoramento</li>
     * </ul>
     *
     * <p>Resposta gerada:
     * <ul>
     *   <li><strong>HTTP Status</strong>: 500 (Internal Server Error)</li>
     *   <li><strong>Código de Erro</strong>: ERRO_INTERNO</li>
     *   <li><strong>Mensagem</strong>: Mensagem genérica sobre erro interno</li>
     *   <li><strong>Detalhes</strong>: Orientação para contatar suporte técnico</li>
     * </ul>
     *
     * <p><strong>Logging</strong>: Todos os detalhes são logados em nível ERROR
     * incluindo stack trace completo para facilitar debugging.
     *
     * @param ex Exceção não tratada pelos handlers específicos
     * @return ResponseEntity com erro genérico e HTTP 500
     * @since 1.0.0
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error during request processing", ex);

        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponse(
            "ERRO_INTERNO",
            "Erro interno durante o processamento",
            "Contacte o suporte técnico se o problema persistir"
        );

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, FileErrorReason.UNKNOWN_ERROR, map, request);
    }
}
