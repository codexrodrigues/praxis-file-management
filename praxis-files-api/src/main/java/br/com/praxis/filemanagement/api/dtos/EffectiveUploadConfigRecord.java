package br.com.praxis.filemanagement.api.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * DTO that represents the effective upload configuration returned by the
 * configuration endpoint.
 *
 * <p>The record aggregates the default options applied by the server as well
 * as auxiliary limits for bulk operations, rate limiting and quotas. It also
 * exposes the friendly error messages so that clients can hydrate UI editors
 * with human readable labels.</p>
 */
@Schema(description = "Configuração efetiva de upload de arquivos")
public record EffectiveUploadConfigRecord(
    @Schema(description = "Opções de upload efetivas aplicadas pelo servidor")
    FileUploadOptionsRecord options,

    @Schema(description = "Parâmetros relevantes para operações em lote")
    BulkConfig bulk,

    @Schema(description = "Configurações de rate limit")
    RateLimitConfig rateLimit,

    @Schema(description = "Informações de cota por tenant/usuário")
    QuotasConfig quotas,

    @Schema(description = "Mapa de mensagens amigáveis por código de erro")
    Map<String, String> messages,

    @Schema(description = "Metadados adicionais da resposta")
    Metadata metadata
) {
    /**
     * Configurações específicas para uploads em lote.
     */
    @Schema(description = "Configurações para upload em lote")
    public record BulkConfig(
        @Schema(description = "Valor padrão do modo fail-fast")
        boolean failFastModeDefault,

        @Schema(description = "Número máximo de arquivos por lote")
        int maxFilesPerBatch,

        @Schema(description = "Número máximo de uploads concorrentes")
        int maxConcurrentUploads
    ) {}

    /**
     * Informações de rate limiting.
     */
    @Schema(description = "Configurações de rate limiting")
    public record RateLimitConfig(
        @Schema(description = "Indica se rate limiting está habilitado")
        boolean enabled,

        @Schema(description = "Limite de uploads por minuto")
        int perMinute,

        @Schema(description = "Limite de uploads por hora")
        int perHour
    ) {}

    /**
     * Informações de cotas de upload.
     */
    @Schema(description = "Configurações de cotas de upload")
    public record QuotasConfig(
        @Schema(description = "Indica se quotas estão habilitadas")
        boolean enabled,

        @Schema(description = "Limite diário por tenant, se definido")
        Integer tenantMaxPerDay,

        @Schema(description = "Limite diário por usuário, se definido")
        Integer userMaxPerDay
    ) {}

    /**
     * Metadados adicionais retornados junto à configuração.
     */
    @Schema(description = "Metadados da configuração")
    public record Metadata(
        @Schema(description = "Versão da biblioteca")
        String version,

        @Schema(description = "Locale padrão das mensagens")
        String locale
    ) {}
}
