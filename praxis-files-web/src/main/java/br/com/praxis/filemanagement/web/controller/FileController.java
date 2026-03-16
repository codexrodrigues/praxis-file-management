package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.BulkUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.utils.ErrorMessageUtils;
import br.com.praxis.filemanagement.core.services.QuotaService;
import br.com.praxis.filemanagement.core.services.PresignedUrlService;
import br.com.praxis.filemanagement.core.exception.QuotaExceededException;
import br.com.praxis.filemanagement.web.error.ErrorResponse;
import br.com.praxis.filemanagement.web.response.ApiEnvelopeFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller para upload seguro de arquivos com documentação OpenAPI completa.
 * Focado apenas no mapeamento HTTP/REST com validações automáticas.
 *
 * @author ErgonX
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/files")
@Tag(
    name = "File Management",
    description = "API para upload e gerenciamento seguro de arquivos"
)
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final FileService fileService;
    private final ObjectMapper objectMapper;
    private final QuotaService quotaService;
    private final PresignedUrlService presignedUrlService;

    public FileController(FileService fileService,
                          ObjectMapper objectMapper,
                          QuotaService quotaService,
                          PresignedUrlService presignedUrlService) {
        this.fileService = fileService;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
        this.presignedUrlService = presignedUrlService;
    }

    /**
     * Endpoint para upload seguro de arquivo único com validações automáticas de segurança.
     *
     * <p>Este endpoint realiza upload de um arquivo aplicando todas as validações de segurança
     * configuradas, incluindo:
     * <ul>
     *   <li>Verificação de tipo MIME e extensão</li>
     *   <li>Validação de tamanho máximo</li>
     *   <li>Escaneamento opcional de vírus</li>
     *   <li>Verificação de números mágicos (magic numbers)</li>
     *   <li>Política de conflito de nomes</li>
     * </ul>
     *
     * @param file Arquivo multipart a ser enviado (obrigatório)
     * @param optionsJson Opções de upload em formato JSON (opcional). Se não fornecido,
     *                    utiliza configurações padrão do sistema
     * @return ResponseEntity contendo resultado padronizado do upload:
     *         <ul>
     *           <li>201 (CREATED) - Upload realizado com sucesso</li>
     *           <li>400 (BAD_REQUEST) - Erro de validação (arquivo inválido, muito grande, etc.)</li>
     *           <li>401 (UNAUTHORIZED) - Autenticação necessária</li>
     *           <li>413 (PAYLOAD_TOO_LARGE) - Arquivo excede limite configurado</li>
     *           <li>415 (UNSUPPORTED_MEDIA_TYPE) - Tipo de arquivo não permitido</li>
     *           <li>429 (TOO_MANY_REQUESTS) - Limite de taxa excedido</li>
     *         </ul>
     * @throws JsonProcessingException Se o JSON de opções fornecido for inválido
     * @since 1.0.0
     */
    @Operation(
        summary = "Upload de arquivo",
        description = "Realiza upload seguro de um arquivo com validações automáticas de segurança, " +
                     "incluindo verificação de tipo MIME, tamanho e escaneamento opcional de vírus.",
        operationId = "uploadFile"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Arquivo enviado com sucesso",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Upload bem-sucedido",
                    value = """
                        {
                          "status": "success",
                          "message": "Upload realizado com sucesso",
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "data": {
                            "originalFilename": "documento.pdf",
                            "serverFilename": "doc_a1b2c3d4.pdf",
                            "fileSize": 1048576,
                            "mimeType": "application/pdf",
                            "uploadTimestamp": "2025-07-10T23:15:30.123Z"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Erro de validação do arquivo",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Vírus detectado",
                    value = """
                        {
                          "status": "failure",
                          "message": "Malware detectado no arquivo",
                          "errors": [
                            {
                              "code": "MALWARE_DETECTADO",
                              "message": "Malware detectado no arquivo",
                              "details": "Vírus identificado: EICAR-Test-File"
                            }
                          ],
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Autenticação necessária",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "status": "failure",
                          "message": "Credenciais de autenticação são necessárias",
                          "errors": [
                            {
                              "code": "NAO_AUTORIZADO",
                              "message": "Credenciais de autenticação são necessárias"
                            }
                          ],
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "413",
            description = "Arquivo muito grande",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Arquivo excede limite",
                    value = """
                        {
                          "status": "failure",
                          "message": "Arquivo excede o tamanho máximo permitido",
                          "errors": [
                            {
                              "code": "ARQUIVO_MUITO_GRANDE",
                              "message": "Arquivo excede o tamanho máximo permitido",
                              "details": "Tamanho do arquivo: 104857600 bytes. Tamanho máximo: 10 MB"
                            }
                          ],
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "415",
            description = "Tipo de arquivo não suportado",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "MIME não permitido",
                    value = """
                        {
                          "status": "failure",
                          "message": "Tipo de arquivo não permitido",
                          "errors": [
                            {
                              "code": "TIPO_ARQUIVO_INVALIDO",
                              "message": "Tipo de arquivo não permitido",
                              "details": "Content-Type: application/x-msdownload"
                            }
                          ],
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Arquivo já existe",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Arquivo duplicado",
                    value = """
                        {
                          "status": "failure",
                          "message": "Arquivo com este nome já existe",
                          "errors": [
                            {
                              "code": "ARQUIVO_JA_EXISTE",
                              "message": "Arquivo com este nome já existe",
                              "details": "Renomeie o arquivo ou escolha outra política"
                            }
                          ],
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Limite de taxa excedido",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Rate limit",
                    value = """
                        {
                          "status": "failure",
                          "message": "Limite de taxa de upload excedido",
                          "errors": [
                            {
                              "code": "LIMITE_TAXA_EXCEDIDO",
                              "message": "Limite de taxa de upload excedido",
                              "details": "Tente novamente em 30 segundos"
                            }
                          ],
                          "timestamp": "2025-07-10T23:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        )
    })
    @PostMapping(
        value = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadSingleFile(
            @Parameter(
                description = "Arquivo a ser enviado",
                required = true,
                content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("file") MultipartFile file,

            @Parameter(
                description = "Opções de upload em formato JSON",
                required = false,
                schema = @Schema(implementation = FileUploadOptionsRecord.class),
                examples = {
                    @ExampleObject(
                        name = "Opções básicas",
                        value = """
                            {
                              "nameConflictPolicy": "RENAME",
                              "targetDirectory": "documents",
                              "maxUploadSizeMb": 50
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Opções com validação rigorosa",
                        value = """
                            {
                              "allowedExtensions": ["pdf", "docx"],
                              "acceptMimeTypes": ["application/pdf"],
                              "nameConflictPolicy": "SKIP",
                              "strictValidation": true,
                              "enableVirusScanning": true
                            }
                            """
                    )
                }
            )
            @RequestParam(value = "options", required = false) String optionsJson,

            @Parameter(
                description = "Metadados customizados do arquivo em formato JSON",
                required = false,
                example = "{\"category\":\"invoice\",\"department\":\"finance\"}"
            )
            @RequestParam(value = "metadata", required = false) String metadataJson,

            @Parameter(
                description = "Política de conflito de nomes sobrescrevendo a opção JSON",
                required = false,
                example = "RENAME"
            )
            @RequestParam(value = "conflictPolicy", required = false) String conflictPolicy,

            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) throws JsonProcessingException {

        logger.debug("Received file upload request - filename: '{}', size: {} bytes",
                   file.getOriginalFilename(), file.getSize());

        // Parse das opções de upload
        FileUploadOptionsRecord options = parseSingleUploadOptions(optionsJson, metadataJson, conflictPolicy);

        if (quotaService.isTenantQuotaExceeded(tenantId) || quotaService.isUserQuotaExceeded(userId)) {
            throw new QuotaExceededException("Upload quota exceeded");
        }

        // Delegar completamente para o serviço usando novo método com Records
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        if (result.success()) {
            quotaService.recordTenantUpload(tenantId);
            quotaService.recordUserUpload(userId);
        }

        // Converter resultado para formato padronizado
        Map<String, Object> response = ApiEnvelopeFactory.fromSingleUploadResult(
            ErrorMessageUtils.convertFileUploadResultRecordToStandardFormat(result),
            "Upload realizado com sucesso"
        );

        // Retornar resposta apropriada
        HttpStatus status;
        if (result.success()) {
            status = HttpStatus.CREATED;
        } else if (isUnsupportedMedia(result)) {
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(response);
    }

    private FileUploadOptionsRecord parseSingleUploadOptions(
            String optionsJson,
            String metadataJson,
            String conflictPolicy
    ) throws JsonProcessingException {
        FileUploadOptionsRecord jsonOptions = null;
        if (optionsJson != null && !optionsJson.trim().isEmpty()) {
            try {
                jsonOptions = objectMapper.readValue(optionsJson, FileUploadOptionsRecord.class);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse options JSON", e);
                throw new JsonProcessingException("Invalid options JSON format") {};
            }
        }

        Map<String, String> customMetadata = jsonOptions != null
                ? new HashMap<>(jsonOptions.customMetadata())
                : new HashMap<>();

        if (metadataJson != null && !metadataJson.trim().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMetadata = objectMapper.readValue(metadataJson, Map.class);
                rawMetadata.forEach((key, value) -> customMetadata.put(key, value != null ? String.valueOf(value) : null));
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse metadata JSON", e);
                throw new JsonProcessingException("Invalid metadata JSON format") {};
            }
        }

        NameConflictPolicy resolvedConflictPolicy = jsonOptions != null
                ? jsonOptions.nameConflictPolicy()
                : null;
        if (conflictPolicy != null && !conflictPolicy.trim().isEmpty()) {
            try {
                resolvedConflictPolicy = NameConflictPolicy.valueOf(conflictPolicy.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new JsonProcessingException("Invalid conflictPolicy value") {};
            }
        }

        boolean hasOverrides = !customMetadata.isEmpty() || resolvedConflictPolicy != null;
        if (jsonOptions == null && !hasOverrides) {
            return null;
        }

        FileUploadOptionsRecord.Builder builder = FileUploadOptionsRecord.builder();
        if (jsonOptions != null) {
            builder.allowedExtensions(jsonOptions.allowedExtensions())
                    .acceptMimeTypes(jsonOptions.acceptMimeTypes())
                    .nameConflictPolicy(jsonOptions.nameConflictPolicy())
                    .maxUploadSizeMb(jsonOptions.maxUploadSizeMb())
                    .strictValidation(jsonOptions.strictValidation())
                    .targetDirectory(jsonOptions.targetDirectory())
                    .enableVirusScanning(jsonOptions.enableVirusScanning())
                    .customMetadata(jsonOptions.customMetadata())
                    .failFastMode(jsonOptions.failFastMode());
        }
        if (resolvedConflictPolicy != null) {
            builder.nameConflictPolicy(resolvedConflictPolicy);
        }
        if (!customMetadata.isEmpty()) {
            builder.customMetadata(customMetadata);
        }
        return builder.build();
    }

    /**
     * Generates a pre-signed upload URL for external storage systems.
     *
     * <p>This endpoint intentionally keeps its raw contract because the frontend
     * consumes it as a transport target descriptor rather than as a standard API
     * business response envelope.
     */
    @PostMapping(value = "/upload/presign", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createPresignedUpload(@RequestParam("filename") String filename) {
        String url = presignedUrlService.createUploadUrl(filename);
        return Map.of(
            "uploadUrl", url,
            "headers", Map.<String, String>of(),
            "fields", Map.<String, String>of()
        );
    }

    /**
     * Endpoint para upload múltiplo de arquivos com processamento paralelo e estatísticas consolidadas.
     *
     * <p>Este endpoint permite upload simultâneo de múltiplos arquivos com as seguintes características:
     * <ul>
     *   <li><strong>Processamento paralelo</strong>: Arquivos são processados simultaneamente para melhor performance</li>
     *   <li><strong>Validação individual</strong>: Cada arquivo é validado independentemente</li>
     *   <li><strong>Estatísticas consolidadas</strong>: Retorna métricas detalhadas do lote</li>
     *   <li><strong>Modo fail-fast</strong>: Opcional - interrompe no primeiro erro</li>
     *   <li><strong>Resultados individuais</strong>: Lista detalhada do resultado de cada arquivo</li>
     * </ul>
     *
     * <p><strong>Parâmetros de configuração:</strong>
     * <ul>
     *   <li>Opções podem ser fornecidas via JSON ou parâmetros diretos</li>
     *   <li>Parâmetros diretos têm precedência sobre JSON</li>
     *   <li>Mesmas opções são aplicadas a todos os arquivos do lote</li>
     * </ul>
     *
     * @param files Array de arquivos multipart para upload (obrigatório, mínimo 1 arquivo)
     * @param optionsJson Opções de upload em formato JSON aplicadas a todos os arquivos (opcional)
     * @param failFastModeParam Parâmetro direto para modo fail-fast - sobrescreve JSON (opcional)
     * @param strictValidationParam Parâmetro direto para validação rigorosa - sobrescreve JSON (opcional)
     * @param maxUploadSizeMbParam Parâmetro direto para tamanho máximo em MB - sobrescreve JSON (opcional)
     * @return ResponseEntity contendo resultado consolidado do upload múltiplo:
     *         <ul>
     *           <li>201 (CREATED) - Todos os arquivos foram enviados com sucesso</li>
     *           <li>207 (MULTI_STATUS) - Alguns arquivos foram enviados, outros falharam</li>
     *           <li>400 (BAD_REQUEST) - Todos os arquivos falharam ou erro na requisição</li>
     *           <li>413 (PAYLOAD_TOO_LARGE) - Payload total muito grande</li>
     *           <li>429 (TOO_MANY_REQUESTS) - Limite de taxa excedido</li>
     *         </ul>
     * @since 1.0.0
     */
    @Operation(
        summary = "Upload múltiplo de arquivos",
        description = "Realiza upload de múltiplos arquivos simultaneamente com processamento paralelo. " +
                     "Cada arquivo é validado individualmente conforme as configurações de segurança. " +
                     "Retorna estatísticas consolidadas e resultados individuais para cada arquivo.",
        operationId = "uploadMultipleFiles"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "207",
            description = "Upload múltiplo processado (alguns podem ter falhado)",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = {
                    @ExampleObject(
                        name = "Upload múltiplo com sucessos e falhas",
                        value = """
                            {
                              "status": "partial_success",
                              "message": "Upload em lote processado com sucesso parcial",
                              "timestamp": "2025-07-21T20:15:35.456Z",
                              "data": {
                                "totalProcessed": 3,
                                "totalSuccess": 2,
                                "totalFailed": 1,
                                "totalCancelled": 0,
                                "wasFailFastTriggered": false,
                                "overallSuccess": false,
                                "processingTimeMs": 5333,
                                "totalSizeBytes": 3145728,
                                "successRate": "66.67%",
                                "results": [
                                  {
                                    "fileName": "doc1.pdf",
                                    "status": "SUCCESS",
                                    "file": {
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "fileName": "doc1.pdf",
                                      "contentType": "application/pdf",
                                      "fileSize": 1048576,
                                      "uploadedAt": "2025-07-21T20:15:31.123Z",
                                      "tenantId": "",
                                      "scanStatus": "PENDING",
                                      "metadata": {}
                                    }
                                  },
                                  {
                                    "fileName": "large_file.exe",
                                    "status": "FAILED",
                                    "error": {
                                      "code": "ARQUIVO_MUITO_GRANDE",
                                      "message": "Arquivo excede o tamanho máximo permitido",
                                      "details": "Reduza o tamanho do arquivo ou divida em partes menores"
                                    }
                                  }
                                ]
                              }
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Colisão de nome",
                        value = """
                            {
                              "status": "partial_success",
                              "message": "Upload em lote processado com sucesso parcial",
                              "timestamp": "2025-07-21T20:15:35.456Z",
                              "data": {
                                "results": [
                                  {
                                    "fileName": "doc1.pdf",
                                    "status": "SUCCESS",
                                    "file": {
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "fileName": "doc1.pdf",
                                      "contentType": "application/pdf",
                                      "fileSize": 1048576,
                                      "uploadedAt": "2025-07-21T20:15:31.123Z",
                                      "tenantId": "",
                                      "scanStatus": "PENDING",
                                      "metadata": {}
                                    }
                                  },
                                  {
                                    "fileName": "doc1.pdf",
                                    "status": "FAILED",
                                    "error": {
                                      "code": "ARQUIVO_JA_EXISTE",
                                      "message": "Arquivo com este nome já existe",
                                      "details": "Escolha um nome diferente ou configure a política de conflito"
                                    }
                                  }
                                ]
                              }
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Vírus detectado",
                        value = """
                            {
                              "status": "partial_success",
                              "message": "Upload em lote processado com sucesso parcial",
                              "timestamp": "2025-07-21T20:15:35.456Z",
                              "data": {
                                "results": [
                                  {
                                    "fileName": "ok.pdf",
                                    "status": "SUCCESS",
                                    "file": {
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "fileName": "ok.pdf",
                                      "contentType": "application/pdf",
                                      "fileSize": 1048576,
                                      "uploadedAt": "2025-07-21T20:15:31.123Z",
                                      "tenantId": "",
                                      "scanStatus": "PENDING",
                                      "metadata": {}
                                    }
                                  },
                                  {
                                    "fileName": "infected.exe",
                                    "status": "FAILED",
                                    "error": {
                                      "code": "MALWARE_DETECTADO",
                                      "message": "Malware detectado no arquivo",
                                      "details": "O arquivo foi identificado como malicioso pelo antivírus"
                                    }
                                  }
                                ]
                              }
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "201",
            description = "Todos os arquivos foram enviados com sucesso",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Upload múltiplo totalmente bem-sucedido",
                    value = """
                        {
                          "status": "success",
                          "message": "Upload em lote realizado com sucesso",
                          "timestamp": "2025-07-21T20:15:35.456Z",
                          "data": {
                            "totalProcessed": 2,
                            "totalSuccess": 2,
                            "totalFailed": 0,
                            "overallSuccess": true,
                            "processingTimeMs": 3200,
                            "successRate": "100.00%"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Erro na requisição (nenhum arquivo enviado, tamanho total excedido, etc.)",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Erro de validação bulk",
                    value = """
                        {
                          "status": "failure",
                          "message": "Tamanho total dos arquivos excede o limite permitido",
                          "errors": [
                            {
                              "code": "BULK_UPLOAD_ERROR",
                              "message": "Tamanho total dos arquivos excede o limite permitido",
                              "details": "Total: 157286400 bytes. Máximo permitido: 100 MB"
                            }
                          ],
                          "timestamp": "2025-07-21T20:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "413",
            description = "Payload muito grande (muitos arquivos ou tamanho total excedido)",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Payload excedido",
                    value = """
                        {
                          "status": "failure",
                          "message": "Tamanho total dos arquivos excede o limite permitido",
                          "errors": [
                            {
                              "code": "PAYLOAD_TOO_LARGE",
                              "message": "Tamanho total dos arquivos excede o limite permitido"
                            }
                          ],
                          "timestamp": "2025-07-21T20:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Limite de taxa excedido para upload múltiplo",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Rate limit bulk",
                    value = """
                        {
                          "status": "failure",
                          "message": "Limite de taxa de upload excedido",
                          "errors": [
                            {
                              "code": "LIMITE_TAXA_EXCEDIDO",
                              "message": "Limite de taxa de upload excedido",
                              "details": "Tente novamente em 60 segundos"
                            }
                          ],
                          "timestamp": "2025-07-21T20:15:30.123Z",
                          "traceId": "abc123"
                        }
                        """
                )
            )
        )
    })
    @PostMapping(
        value = {"/upload/bulk", "/bulk"},
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadMultipleFiles(
            @Parameter(
                description = "Array de arquivos a serem enviados simultaneamente",
                required = true,
                content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("files") MultipartFile[] files,

            @Parameter(
                description = "Opções de upload aplicadas a todos os arquivos em formato JSON",
                required = false,
                schema = @Schema(implementation = FileUploadOptionsRecord.class),
                examples = {
                    @ExampleObject(
                        name = "Opções para upload múltiplo",
                        value = """
                            {
                              "nameConflictPolicy": "RENAME",
                              "targetDirectory": "bulk-uploads",
                              "maxUploadSizeMb": 25,
                              "enableVirusScanning": true,
                              "strictValidation": true,
                              "allowedExtensions": ["pdf", "docx", "xlsx", "png", "jpg"]
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Upload múltiplo restritivo",
                        value = """
                            {
                              "acceptMimeTypes": ["application/pdf", "image/jpeg", "image/png"],
                              "nameConflictPolicy": "SKIP",
                              "maxUploadSizeMb": 10,
                              "enableVirusScanning": true,
                              "strictValidation": true
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Upload com fail-fast mode",
                        value = """
                            {
                              "failFastMode": true,
                              "maxUploadSizeMb": 50,
                              "strictValidation": true,
                              "nameConflictPolicy": "RENAME"
                            }
                            """
                    )
                }
            )
            @RequestParam(value = "options", required = false) String optionsJson,

            @Parameter(
                description = "Array JSON de metadados por arquivo. Atualmente o backend aceita valores homogêneos no lote.",
                required = false,
                example = "[{\"category\":\"invoice\"},{\"category\":\"invoice\"}]"
            )
            @RequestParam(value = "metadata", required = false) String metadataJson,

            @Parameter(
                description = "Array JSON de políticas de conflito por arquivo. Atualmente o backend aceita valores homogêneos no lote.",
                required = false,
                example = "[\"RENAME\",\"RENAME\"]"
            )
            @RequestParam(value = "conflictPolicy", required = false) String conflictPolicyJson,

            // Parâmetros diretos para facilitar uso (opcional)
            @Parameter(
                description = "Ativar modo fail-fast (para no primeiro erro)",
                required = false,
                example = "true"
            )
            @RequestParam(value = "failFastMode", required = false) Boolean failFastModeParam,

            @Parameter(
                description = "Ativar validação rigorosa",
                required = false,
                example = "true"
            )
            @RequestParam(value = "strictValidation", required = false) Boolean strictValidationParam,

            @Parameter(
                description = "Tamanho máximo do arquivo em MB",
                required = false,
                example = "50"
            )
            @RequestParam(value = "maxUploadSizeMb", required = false) Long maxUploadSizeMbParam,

            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {

        logger.debug("Received bulk upload request");
        logger.debug("Received bulk upload request with {} files", files != null ? files.length : 0);
        logger.debug("Direct parameters: failFast={}, strict={}, maxSize={}",
                    failFastModeParam, strictValidationParam, maxUploadSizeMbParam);

        // Validação básica
        if (files == null || files.length == 0) {
            logger.warn("Bulk upload request received with no files");

            Map<String, Object> errorResponse = ApiEnvelopeFactory.failure(
                "NO_FILES_PROVIDED",
                "Nenhum arquivo foi fornecido para upload",
                "Envie pelo menos um arquivo no campo 'files'"
            );

            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Log de segurança para uploads múltiplos grandes
        if (files.length > 10) {
            logger.info("Large bulk upload attempted: {} files from client", files.length);
        }

        // Parse das opções de upload com suporte a parâmetros diretos
        FileUploadOptionsRecord options;
        try {
            options = parseUploadOptions(
                optionsJson,
                metadataJson,
                conflictPolicyJson,
                files != null ? files.length : 0,
                failFastModeParam,
                strictValidationParam,
                maxUploadSizeMbParam
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse upload options: {}", e.getMessage());
            Map<String, Object> errorResponse = ApiEnvelopeFactory.failure(
                "INVALID_OPTIONS_FORMAT",
                "Erro ao processar opções de upload",
                e.getMessage()
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (quotaService.isTenantQuotaExceeded(tenantId) || quotaService.isUserQuotaExceeded(userId)) {
            throw new QuotaExceededException("Upload quota exceeded");
        }

        // Delegar para o serviço - método único sem ambiguidade
        logger.debug("Bulk upload: calling uploadMultipleFiles with {} files", files.length);
        BulkUploadResultRecord bulkResult = fileService.uploadMultipleFiles(files, options);

        if (bulkResult.results() != null) {
            bulkResult.results().stream()
                .filter(FileUploadResultRecord::success)
                .forEach(r -> {
                    quotaService.recordTenantUpload(tenantId);
                    quotaService.recordUserUpload(userId);
                });
        }

        // Converter resultado para formato padronizado
        Map<String, Object> response = convertBulkUploadResultToStandardFormat(bulkResult);

        // Determinar status HTTP apropriado
        HttpStatus status;
        if (bulkResult.overallSuccess()) {
            status = HttpStatus.CREATED; // 201 - todos os arquivos foram criados
        } else if (bulkResult.totalSuccess() > 0) {
            status = HttpStatus.MULTI_STATUS; // 207 - alguns sucessos, algumas falhas
        } else if (bulkResult.getFailedResults().stream().allMatch(this::isUnsupportedMedia)) {
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE; // 415 - todos falharam por tipo não suportado
        } else {
            status = HttpStatus.BAD_REQUEST; // 400 - todos falharam
        }

        logger.info("Bulk upload completed: {} total, {} successful, {} failed, status: {}",
            bulkResult.totalProcessed(), bulkResult.totalSuccess(),
            bulkResult.totalFailed(), status.value());

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Converte BulkUploadResultRecord para formato padronizado da API REST.
     *
     * <p>Transforma o resultado interno do upload múltiplo em formato padronizado
     * para resposta HTTP, incluindo estatísticas consolidadas e resultados individuais.
     *
     * @param bulkResult Resultado do processamento de upload múltiplo
     * @return Map contendo resposta formatada com structure:
     *         <ul>
     *           <li>status (String) - `success`, `partial_success` ou `failure`</li>
     *           <li>message (String) - Resumo de negócio coerente com o resultado consolidado</li>
     *           <li>timestamp (String) - Timestamp de conclusão da operação</li>
     *           <li>data (Map) - Dados consolidados incluindo estatísticas e resultados individuais</li>
     *         </ul>
     * @since 1.0.0
     */
    private Map<String, Object> convertBulkUploadResultToStandardFormat(BulkUploadResultRecord bulkResult) {
        Map<String, Object> standardData = new java.util.HashMap<>();
        standardData.put("totalProcessed", bulkResult.totalProcessed());
        standardData.put("totalSuccess", bulkResult.totalSuccess());
        standardData.put("totalFailed", bulkResult.totalFailed());
        standardData.put("totalCancelled", bulkResult.totalCancelled());
        standardData.put("wasFailFastTriggered", bulkResult.wasFailFastTriggered());
        standardData.put("overallSuccess", bulkResult.overallSuccess());
        standardData.put("processingTimeMs", bulkResult.processingTimeMs());
        standardData.put("totalSizeBytes", bulkResult.totalSizeBytes());
        standardData.put("successRate", String.format(java.util.Locale.US, "%.2f%%", bulkResult.getSuccessRate()));
        java.util.List<Map<String, Object>> standardResults = bulkResult.results().stream()
            .map(this::convertBulkUploadItemToFrontendFormat)
            .toList();
        standardData.put("results", standardResults);
        standardData.put("metadata", bulkResult.metadata());

        boolean partialSuccess = !bulkResult.overallSuccess() && bulkResult.totalSuccess() > 0;
        String message = bulkResult.overallSuccess()
            ? "Upload em lote realizado com sucesso"
            : (partialSuccess
                ? "Upload em lote processado com sucesso parcial"
                : "Upload em lote falhou");
        String responseStatus = bulkResult.overallSuccess()
            ? "success"
            : (partialSuccess ? "partial_success" : "failure");

        return ApiEnvelopeFactory.response(
            responseStatus,
            message,
            standardData,
            Map.of()
        );
    }

    private Map<String, Object> convertBulkUploadItemToFrontendFormat(FileUploadResultRecord result) {
        Map<String, Object> standardFormat = ErrorMessageUtils.convertFileUploadResultRecordToStandardFormat(result);
        Map<String, Object> frontendItem = new java.util.HashMap<>();
        frontendItem.put("fileName", result.originalFilename());
        frontendItem.put("status", result.success() ? "SUCCESS" : "FAILED");

        if (result.success()) {
            frontendItem.put("file", Map.of(
                "id", result.fileId() != null ? result.fileId() : "",
                "fileName", result.originalFilename(),
                "contentType", result.mimeType() != null ? result.mimeType() : "",
                "fileSize", result.fileSize(),
                "uploadedAt", result.uploadTimestamp() != null ? result.uploadTimestamp().toString() : java.time.Instant.now().toString(),
                "tenantId", "",
                "scanStatus", "PENDING",
                "metadata", result.metadata() != null ? result.metadata() : Map.of()
            ));
        } else {
            String code = (String) standardFormat.getOrDefault("code", "ERRO_DESCONHECIDO");
            String message = (String) standardFormat.getOrDefault("message", "Erro no upload");
            Object details = standardFormat.getOrDefault("details", result.errorMessage());
            frontendItem.put("error", Map.of(
                "status", "failure",
                "message", message,
                "errors", List.of(
                    details != null
                        ? Map.of("code", code, "message", message, "details", details)
                        : Map.of("code", code, "message", message)
                ),
                "timestamp", standardFormat.getOrDefault("timestamp", java.time.Instant.now().toString())
            ));
        }

        return Map.copyOf(frontendItem);
    }

    /**
     * Analisa e mescla opções de upload de JSON e parâmetros diretos da requisição.
     *
     * <p>Este método permite flexibilidade na configuração de uploads múltiplos:
     * <ul>
     *   <li><strong>Apenas JSON</strong>: Retorna opções parseadas do JSON</li>
     *   <li><strong>Apenas parâmetros</strong>: Cria opções com valores diretos e defaults</li>
     *   <li><strong>Ambos</strong>: Mescla com precedência para parâmetros diretos</li>
     *   <li><strong>Nenhum</strong>: Retorna null (usa defaults do serviço)</li>
     * </ul>
     *
     * @param optionsJson String JSON com opções de upload (pode ser null)
     * @param failFastModeParam Parâmetro direto para modo fail-fast (pode ser null)
     * @param strictValidationParam Parâmetro direto para validação rigorosa (pode ser null)
     * @param maxUploadSizeMbParam Parâmetro direto para tamanho máximo em MB (pode ser null)
     * @return FileUploadOptionsRecord mesclado ou null se nenhuma opção fornecida
     * @throws JsonProcessingException Se o JSON fornecido for inválido
     * @since 1.0.0
     */
    private FileUploadOptionsRecord parseUploadOptions(
            String optionsJson,
            String metadataJson,
            String conflictPolicyJson,
            int fileCount,
            Boolean failFastModeParam,
            Boolean strictValidationParam,
            Long maxUploadSizeMbParam) throws JsonProcessingException {

        FileUploadOptionsRecord jsonOptions = null;
        if (optionsJson != null && !optionsJson.trim().isEmpty()) {
            try {
                jsonOptions = objectMapper.readValue(optionsJson, FileUploadOptionsRecord.class);
                logger.debug("Parsed JSON options: {}", optionsJson);
            } catch (JsonProcessingException e) {
                logger.error("Failed to parse JSON options: {}", optionsJson, e);
                throw e;
            }
        }

        boolean hasDirectParams = failFastModeParam != null ||
                                 strictValidationParam != null ||
                                 maxUploadSizeMbParam != null;

        if (!hasDirectParams) {
            if (jsonOptions != null) {
                if (jsonOptions.maxUploadSizeMb() != null && jsonOptions.maxUploadSizeMb() <= 0) {
                    jsonOptions = FileUploadOptionsRecord.builder()
                        .allowedExtensions(jsonOptions.allowedExtensions())
                        .acceptMimeTypes(jsonOptions.acceptMimeTypes())
                        .nameConflictPolicy(jsonOptions.nameConflictPolicy())
                        .strictValidation(jsonOptions.strictValidation())
                        .targetDirectory(jsonOptions.targetDirectory())
                        .enableVirusScanning(jsonOptions.enableVirusScanning())
                        .customMetadata(jsonOptions.customMetadata())
                        .failFastMode(jsonOptions.failFastMode())
                        .build();
                }
                return applyBulkCompatibilityOverrides(jsonOptions, metadataJson, conflictPolicyJson, fileCount);
            }
            return applyBulkCompatibilityOverrides(null, metadataJson, conflictPolicyJson, fileCount);
        }

        var builder = FileUploadOptionsRecord.builder();
        if (jsonOptions != null) {
            builder.allowedExtensions(jsonOptions.allowedExtensions())
                   .acceptMimeTypes(jsonOptions.acceptMimeTypes())
                   .nameConflictPolicy(jsonOptions.nameConflictPolicy())
                   .strictValidation(jsonOptions.strictValidation())
                   .targetDirectory(jsonOptions.targetDirectory())
                   .enableVirusScanning(jsonOptions.enableVirusScanning())
                   .customMetadata(jsonOptions.customMetadata())
                   .failFastMode(jsonOptions.failFastMode());
            if (jsonOptions.maxUploadSizeMb() != null && jsonOptions.maxUploadSizeMb() > 0) {
                builder.maxUploadSizeMb(jsonOptions.maxUploadSizeMb());
            }
        } else {
            builder.failFastMode(false);
        }

        if (failFastModeParam != null) {
            builder.failFastMode(failFastModeParam);
            logger.debug("Direct parameter: failFastMode = {}", failFastModeParam);
        }
        if (strictValidationParam != null) {
            builder.strictValidation(strictValidationParam);
            logger.debug("Direct parameter: strictValidation = {}", strictValidationParam);
        }
        if (maxUploadSizeMbParam != null) {
            if (maxUploadSizeMbParam > 0) {
                builder.maxUploadSizeMb(maxUploadSizeMbParam);
                logger.debug("Direct parameter: maxUploadSizeMb = {}", maxUploadSizeMbParam);
            } else {
                logger.debug("Direct parameter: maxUploadSizeMb <= 0, will use server default");
            }
        }

        FileUploadOptionsRecord mergedOptions = builder.build();
        mergedOptions = applyBulkCompatibilityOverrides(mergedOptions, metadataJson, conflictPolicyJson, fileCount);
        logger.debug("Merged upload options: failFast={}, strict={}, maxSize={}MB",
                    mergedOptions.failFastMode(),
                    mergedOptions.strictValidation(),
                    mergedOptions.maxUploadSizeMb());

        return mergedOptions;
    }

    private FileUploadOptionsRecord applyBulkCompatibilityOverrides(
            FileUploadOptionsRecord baseOptions,
            String metadataJson,
            String conflictPolicyJson,
            int fileCount) throws JsonProcessingException {
        Map<String, String> customMetadata = baseOptions != null
                ? new HashMap<>(baseOptions.customMetadata())
                : new HashMap<>();

        if (metadataJson != null && !metadataJson.trim().isEmpty()) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> metadataItems = objectMapper.readValue(metadataJson, java.util.List.class);
            validateBulkCompatibilityArrayLength(metadataItems.size(), fileCount, "metadata");

            Map<String, String> firstNonNullMetadata = null;
            for (Object item : metadataItems) {
                if (item == null) {
                    continue;
                }
                if (!(item instanceof Map<?, ?> rawMap)) {
                    throw new JsonProcessingException("Invalid metadata JSON format") {};
                }
                Map<String, String> normalizedMap = new HashMap<>();
                rawMap.forEach((key, value) -> normalizedMap.put(String.valueOf(key), value != null ? String.valueOf(value) : null));
                if (firstNonNullMetadata == null) {
                    firstNonNullMetadata = normalizedMap;
                } else if (!firstNonNullMetadata.equals(normalizedMap)) {
                    throw new JsonProcessingException("Per-file metadata is not supported for bulk uploads unless all entries are equal") {};
                }
            }

            if (firstNonNullMetadata != null) {
                customMetadata.putAll(firstNonNullMetadata);
            }
        }

        NameConflictPolicy resolvedConflictPolicy = baseOptions != null
                ? baseOptions.nameConflictPolicy()
                : null;
        if (conflictPolicyJson != null && !conflictPolicyJson.trim().isEmpty()) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> policyItems = objectMapper.readValue(conflictPolicyJson, java.util.List.class);
            validateBulkCompatibilityArrayLength(policyItems.size(), fileCount, "conflictPolicy");

            NameConflictPolicy firstNonNullPolicy = null;
            for (Object item : policyItems) {
                if (item == null) {
                    continue;
                }
                NameConflictPolicy normalizedPolicy;
                try {
                    normalizedPolicy = NameConflictPolicy.valueOf(String.valueOf(item).trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    throw new JsonProcessingException("Invalid conflictPolicy value") {};
                }
                if (firstNonNullPolicy == null) {
                    firstNonNullPolicy = normalizedPolicy;
                } else if (firstNonNullPolicy != normalizedPolicy) {
                    throw new JsonProcessingException("Per-file conflictPolicy is not supported for bulk uploads unless all entries are equal") {};
                }
            }

            if (firstNonNullPolicy != null) {
                resolvedConflictPolicy = firstNonNullPolicy;
            }
        }

        boolean hasCompatibilityOverrides = !customMetadata.isEmpty() || resolvedConflictPolicy != null;
        if (baseOptions == null && !hasCompatibilityOverrides) {
            return null;
        }

        FileUploadOptionsRecord.Builder builder = FileUploadOptionsRecord.builder();
        if (baseOptions != null) {
            builder.allowedExtensions(baseOptions.allowedExtensions())
                    .acceptMimeTypes(baseOptions.acceptMimeTypes())
                    .nameConflictPolicy(baseOptions.nameConflictPolicy())
                    .maxUploadSizeMb(baseOptions.maxUploadSizeMb())
                    .strictValidation(baseOptions.strictValidation())
                    .targetDirectory(baseOptions.targetDirectory())
                    .enableVirusScanning(baseOptions.enableVirusScanning())
                    .customMetadata(baseOptions.customMetadata())
                    .failFastMode(baseOptions.failFastMode());
        }
        if (resolvedConflictPolicy != null) {
            builder.nameConflictPolicy(resolvedConflictPolicy);
        }
        if (!customMetadata.isEmpty()) {
            builder.customMetadata(customMetadata);
        }
        return builder.build();
    }

    private void validateBulkCompatibilityArrayLength(int receivedLength, int fileCount, String fieldName) throws JsonProcessingException {
        if (fileCount > 0 && receivedLength != fileCount) {
            throw new JsonProcessingException("Bulk field '" + fieldName + "' length must match files length") {};
        }
    }

    /**
     * Checks if a single file result failed due to unsupported media type.
     */
    private boolean isUnsupportedMedia(FileUploadResultRecord result) {
        if (result == null || result.success()) {
            return false;
        }
        FileErrorReason reason = result.errorReason();
        return reason == FileErrorReason.MIME_TYPE_MISMATCH
                || reason == FileErrorReason.SIGNATURE_MISMATCH
                || reason == FileErrorReason.MAGIC_NUMBER_MISMATCH;
    }

}
