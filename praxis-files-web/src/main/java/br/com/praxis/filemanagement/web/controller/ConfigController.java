package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.EffectiveUploadConfigRecord;
import br.com.praxis.filemanagement.web.service.FileEffectiveConfigService;
import br.com.praxis.filemanagement.web.error.ErrorResponse;
import br.com.praxis.filemanagement.web.response.ApiEnvelopeFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller exposing the configuration endpoint.
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "File Management", description = "API para upload e gerenciamento seguro de arquivos")
public class ConfigController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigController.class);

    private final FileEffectiveConfigService configService;

    public ConfigController(FileEffectiveConfigService configService) {
        this.configService = configService;
    }

    /**
     * Returns the effective upload configuration that will be applied by the server.
     *
     * @param tenantId optional tenant identifier
     * @param userId   optional user identifier
     * @return standard envelope with the effective configuration
     */
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Recupera a configuração efetiva de upload",
        description = "Retorna as opções de upload, limites de bulk, rate limit, quotas e mensagens amigáveis",
        security = @SecurityRequirement(name = "basicAuth"),
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Configuração retornada com sucesso",
                content = @Content(
                    schema = @Schema(implementation = EffectiveUploadConfigRecord.class),
                    examples = @ExampleObject(value = """
                        {
                          "status": "success",
                          "message": "Configuração efetiva recuperada com sucesso",
                          "timestamp": "2025-08-22T12:34:56.789Z",
                          "data": {
                            "options": { "nameConflictPolicy": "RENAME" },
                            "bulk": { "failFastModeDefault": false },
                            "rateLimit": { "enabled": true },
                            "quotas": { "enabled": false },
                            "messages": { "FILE_TOO_LARGE": "Arquivo muito grande." },
                            "metadata": { "version": "1.1.0" }
                          }
                        }
                    """)
                )
            ),
            @ApiResponse(
                responseCode = "401",
                description = "Autenticação necessária",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "403",
                description = "Acesso negado",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Erro interno",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
        }
    )
    public ResponseEntity<Map<String, Object>> getConfig(
        @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
        @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        EffectiveUploadConfigRecord config = configService.getEffectiveConfig(tenantId, userId);
        Map<String, Object> body = ApiEnvelopeFactory.success(
            config,
            "Configuração efetiva recuperada com sucesso"
        );

        String eTag = '"' + Integer.toHexString(config.hashCode()) + '"';
        LOGGER.info("GET /api/files/config tenant={} user={} etag={}", tenantId, userId, eTag);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
            .eTag(eTag)
            .body(body);
    }
}
