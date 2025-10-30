package br.com.praxis.filemanagement.api.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Razões de erro durante o upload de arquivos, organizadas por categoria.
 */
@Schema(description = "Códigos de erro específicos para upload de arquivos, categorizados por tipo")
public enum FileErrorReason {
    
    // ===================================================================================
    // ERROS DE VALIDAÇÃO - Detectados antes do processamento (fail fast)
    // ===================================================================================
    
    @Schema(description = "[VALIDAÇÃO] Arquivo está vazio ou tem tamanho zero")
    EMPTY_FILE,
    
    @Schema(description = "[VALIDAÇÃO] Arquivo excede o tamanho máximo permitido")
    FILE_TOO_LARGE,
    
    @Schema(description = "[VALIDAÇÃO] Tipo de arquivo não permitido ou não suportado")
    INVALID_TYPE,
    
    @Schema(description = "[VALIDAÇÃO] Extensão do arquivo não é permitida")
    UNSUPPORTED_FILE_TYPE,
    
    @Schema(description = "[VALIDAÇÃO] Caminho do arquivo contém caracteres inválidos")
    INVALID_PATH,
    
    @Schema(description = "[VALIDAÇÃO] Nome do arquivo contém padrões suspeitos")
    INVALID_FILENAME,
    
    @Schema(description = "[VALIDAÇÃO] Limite de taxa de upload excedido")
    RATE_LIMIT_EXCEEDED,

    @Schema(description = "[VALIDAÇÃO] Cota de uploads excedida para o cliente")
    QUOTA_EXCEEDED,
    
    // ===================================================================================
    // ERROS DE SEGURANÇA - Detectados durante análise de conteúdo
    // ===================================================================================
    
    @Schema(description = "[SEGURANÇA] Tipo MIME não corresponde à extensão do arquivo")
    MIME_TYPE_MISMATCH,
    
    @Schema(description = "[SEGURANÇA] Assinatura do arquivo não corresponde ao tipo declarado")
    SIGNATURE_MISMATCH,
    
    @Schema(description = "[SEGURANÇA] Magic number não corresponde ao tipo de arquivo")
    MAGIC_NUMBER_MISMATCH,
    
    @Schema(description = "[SEGURANÇA] Arquivo executável perigoso detectado")
    DANGEROUS_EXECUTABLE,
    
    @Schema(description = "[SEGURANÇA] Script malicioso detectado no arquivo")
    DANGEROUS_SCRIPT,
    
    @Schema(description = "[SEGURANÇA] Tipo de arquivo considerado perigoso")
    DANGEROUS_FILE_TYPE,
    
    @Schema(description = "[SEGURANÇA] Malware detectado pelo scanner de vírus")
    MALWARE_DETECTED,
    
    @Schema(description = "[SEGURANÇA] Vírus detectado pelo escaneamento")
    VIRUS_DETECTED,
    
    @Schema(description = "[SEGURANÇA] Scanner de vírus obrigatório não está disponível")
    VIRUS_SCAN_UNAVAILABLE,
    
    @Schema(description = "[SEGURANÇA] Extensão de arquivo suspeita foi bloqueada")
    SUSPICIOUS_EXTENSION_BLOCKED,
    
    @Schema(description = "[SEGURANÇA] Estrutura suspeita detectada no arquivo")
    SUSPICIOUS_STRUCTURE,
    
    @Schema(description = "[SEGURANÇA] Potencial zip bomb detectado")
    ZIP_BOMB_DETECTED,
    
    @Schema(description = "[SEGURANÇA] Executável embutido detectado")
    EMBEDDED_EXECUTABLE,
    
    @Schema(description = "[SEGURANÇA] Tentativa de path traversal detectada")
    PATH_TRAVERSAL,
    
    @Schema(description = "[SEGURANÇA] Violação geral de segurança detectada")
    SECURITY_VIOLATION,
    
    // ===================================================================================
    // ERROS DE PROCESSAMENTO - Problemas durante o upload/armazenamento
    // ===================================================================================
    
    @Schema(description = "[PROCESSAMENTO] Arquivo corrompido ou não legível")
    CORRUPTED_FILE,
    
    @Schema(description = "[PROCESSAMENTO] Arquivo já existe e política não permite sobrescrever")
    FILE_EXISTS,
    
    @Schema(description = "[PROCESSAMENTO] Timeout durante processamento do arquivo")
    UPLOAD_TIMEOUT,
    
    @Schema(description = "[PROCESSAMENTO] Timeout durante operação de upload múltiplo")
    BULK_UPLOAD_TIMEOUT,
    
    @Schema(description = "[PROCESSAMENTO] Erro de I/O durante gravação do arquivo")
    IO_ERROR,
    
    @Schema(description = "[PROCESSAMENTO] Espaço em disco insuficiente")
    INSUFFICIENT_STORAGE,
    
    @Schema(description = "[PROCESSAMENTO] Erro desconhecido durante o processamento")
    UNKNOWN_ERROR,
    
    // ===================================================================================
    // ERROS DE CONTROLE DE FLUXO - Relacionados ao processamento bulk
    // ===================================================================================
    
    @Schema(description = "[CONTROLE] Upload cancelado devido ao modo fail-fast")
    BULK_UPLOAD_CANCELLED,
    
    @Schema(description = "[CONTROLE] Upload interrompido pelo usuário")
    USER_CANCELLED,
    
    @Schema(description = "[CONTROLE] Limite máximo de arquivos por batch excedido")
    BATCH_SIZE_EXCEEDED,
    
    @Schema(description = "[CONTROLE] Nenhum erro encontrado")
    NONE;
    
    // ===================================================================================
    // MÉTODOS UTILITÁRIOS PARA CATEGORIZAÇÃO
    // ===================================================================================
    
    /**
     * Verifica se o erro é uma falha de validação que deve parar o processamento imediatamente.
     */
    public boolean isValidationError() {
        return switch (this) {
            case EMPTY_FILE, FILE_TOO_LARGE, INVALID_TYPE, UNSUPPORTED_FILE_TYPE, 
                 INVALID_PATH, INVALID_FILENAME, RATE_LIMIT_EXCEEDED -> true;
            default -> false;
        };
    }
    
    /**
     * Verifica se o erro é uma falha de segurança que requer auditoria.
     */
    public boolean isSecurityError() {
        return switch (this) {
            case MIME_TYPE_MISMATCH, SIGNATURE_MISMATCH, MAGIC_NUMBER_MISMATCH, 
                 DANGEROUS_EXECUTABLE, DANGEROUS_SCRIPT, DANGEROUS_FILE_TYPE,
                 MALWARE_DETECTED, VIRUS_DETECTED, VIRUS_SCAN_UNAVAILABLE, SUSPICIOUS_EXTENSION_BLOCKED,
                 SUSPICIOUS_STRUCTURE, ZIP_BOMB_DETECTED, EMBEDDED_EXECUTABLE, PATH_TRAVERSAL,
                 SECURITY_VIOLATION -> true;
            default -> false;
        };
    }
    
    /**
     * Verifica se o erro é uma falha de processamento que pode ser temporária.
     */
    public boolean isProcessingError() {
        return switch (this) {
            case CORRUPTED_FILE, FILE_EXISTS, UPLOAD_TIMEOUT, BULK_UPLOAD_TIMEOUT,
                 IO_ERROR, INSUFFICIENT_STORAGE, UNKNOWN_ERROR -> true;
            default -> false;
        };
    }
    
    /**
     * Verifica se o erro é relacionado ao controle de fluxo bulk.
     */
    public boolean isControlFlowError() {
        return switch (this) {
            case BULK_UPLOAD_CANCELLED, USER_CANCELLED, BATCH_SIZE_EXCEEDED -> true;
            default -> false;
        };
    }
    
    /**
     * Retorna a categoria do erro em formato legível.
     */
    public String getCategory() {
        if (isValidationError()) return "VALIDAÇÃO";
        if (isSecurityError()) return "SEGURANÇA";
        if (isProcessingError()) return "PROCESSAMENTO";
        if (isControlFlowError()) return "CONTROLE";
        return "OUTROS";
    }
}
