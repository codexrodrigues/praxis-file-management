package br.com.praxis.filemanagement.core.utils;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitário para padronizar mensagens de erro em todo o sistema
 * Centraliza todas as mensagens de validação e erro em português
 *
 * @author ErgonX
 * @since 1.0.0
 */
public class ErrorMessageUtils {

    /**
     * Mapeamento de códigos de erro para mensagens amigáveis em português
     */
    private static final Map<FileErrorReason, ErrorInfo> ERROR_MESSAGES = new HashMap<>();

    /**
     * Mensagens de erro genéricas do sistema
     */
    private static final Map<String, ErrorInfo> GENERIC_ERROR_MESSAGES = new HashMap<>();

    static {
        initializeErrorMessages();
        initializeGenericErrorMessages();
    }

    private static void initializeErrorMessages() {
        ERROR_MESSAGES.put(FileErrorReason.EMPTY_FILE,
            new ErrorInfo("ARQUIVO_VAZIO", "O arquivo enviado está vazio",
                         "Certifique-se de selecionar um arquivo com conteúdo"));

        ERROR_MESSAGES.put(FileErrorReason.INVALID_TYPE,
            new ErrorInfo("TIPO_ARQUIVO_INVALIDO", "Tipo de arquivo não permitido",
                         "Verifique os tipos de arquivo aceitos pelo sistema"));

        ERROR_MESSAGES.put(FileErrorReason.FILE_TOO_LARGE,
            new ErrorInfo("ARQUIVO_MUITO_GRANDE", "Arquivo excede o tamanho máximo permitido",
                         "Reduza o tamanho do arquivo ou divida em partes menores"));

        ERROR_MESSAGES.put(FileErrorReason.FILE_EXISTS,
            new ErrorInfo("ARQUIVO_JA_EXISTE", "Arquivo com este nome já existe",
                         "Escolha um nome diferente ou configure a política de conflito"));

        ERROR_MESSAGES.put(FileErrorReason.SIGNATURE_MISMATCH,
            new ErrorInfo("ASSINATURA_INVALIDA", "A assinatura do arquivo não corresponde ao tipo declarado",
                         "O arquivo pode estar corrompido ou ter extensão incorreta"));

        ERROR_MESSAGES.put(FileErrorReason.DANGEROUS_EXECUTABLE,
            new ErrorInfo("ARQUIVO_EXECUTAVEL_PERIGOSO", "Arquivo executável detectado",
                         "Arquivos executáveis não são permitidos por questões de segurança"));

        ERROR_MESSAGES.put(FileErrorReason.DANGEROUS_SCRIPT,
            new ErrorInfo("SCRIPT_PERIGOSO", "Script malicioso detectado",
                         "O arquivo contém código que pode ser perigoso"));

        ERROR_MESSAGES.put(FileErrorReason.MALWARE_DETECTED,
            new ErrorInfo("MALWARE_DETECTADO", "Malware detectado no arquivo",
                         "O arquivo foi identificado como malicioso pelo antivírus"));

        ERROR_MESSAGES.put(FileErrorReason.CORRUPTED_FILE,
            new ErrorInfo("ARQUIVO_CORROMPIDO", "Arquivo corrompido ou malformado",
                         "O arquivo não pôde ser processado devido a problemas de integridade"));

        ERROR_MESSAGES.put(FileErrorReason.SUSPICIOUS_STRUCTURE,
            new ErrorInfo("ESTRUTURA_SUSPEITA", "Estrutura suspeita detectada no arquivo",
                         "O arquivo contém elementos que podem representar risco de segurança"));

        ERROR_MESSAGES.put(FileErrorReason.ZIP_BOMB_DETECTED,
            new ErrorInfo("ZIP_BOMB_DETECTADO", "Possível zip bomb detectado",
                         "O arquivo compactado pode causar problemas de desempenho"));

        ERROR_MESSAGES.put(FileErrorReason.EMBEDDED_EXECUTABLE,
            new ErrorInfo("EXECUTAVEL_INCORPORADO", "Arquivo executável incorporado detectado",
                         "O arquivo contém código executável incorporado"));

        ERROR_MESSAGES.put(FileErrorReason.PATH_TRAVERSAL,
            new ErrorInfo("PATH_TRAVERSAL", "Tentativa de path traversal detectada",
                         "O arquivo contém caminhos que podem comprometer a segurança"));

        ERROR_MESSAGES.put(FileErrorReason.INVALID_PATH,
            new ErrorInfo("CAMINHO_INVALIDO", "Caminho de arquivo inválido detectado",
                         "O caminho do arquivo pode comprometer a segurança do sistema"));

        ERROR_MESSAGES.put(FileErrorReason.MIME_TYPE_MISMATCH,
            new ErrorInfo("TIPO_MIME_INCOMPATIVEL", "Tipo MIME não corresponde ao arquivo",
                         "O tipo de arquivo declarado não corresponde ao conteúdo"));

        ERROR_MESSAGES.put(FileErrorReason.VIRUS_DETECTED,
            new ErrorInfo("VIRUS_DETECTADO", "Vírus detectado no arquivo",
                         "O arquivo contém um vírus conhecido"));

        ERROR_MESSAGES.put(FileErrorReason.UNKNOWN_ERROR,
            new ErrorInfo("ERRO_DESCONHECIDO", "Erro interno do sistema",
                         "Contacte o suporte técnico para assistência"));

        ERROR_MESSAGES.put(FileErrorReason.SECURITY_VIOLATION,
            new ErrorInfo("VIOLACAO_SEGURANCA", "Violação de segurança detectada",
                         "O arquivo apresenta características que violam as políticas de segurança"));

        ERROR_MESSAGES.put(FileErrorReason.MAGIC_NUMBER_MISMATCH,
            new ErrorInfo("MAGIC_NUMBER_INCOMPATIVEL", "Magic number não corresponde ao tipo de arquivo",
                         "A assinatura interna do arquivo não corresponde à extensão declarada"));

        ERROR_MESSAGES.put(FileErrorReason.DANGEROUS_FILE_TYPE,
            new ErrorInfo("TIPO_ARQUIVO_PERIGOSO", "Tipo de arquivo considerado perigoso",
                         "Este tipo de arquivo é considerado potencialmente perigoso pelo sistema"));

        ERROR_MESSAGES.put(FileErrorReason.VIRUS_SCAN_UNAVAILABLE,
            new ErrorInfo("SCANNER_VIRUS_INDISPONIVEL", "Scanner de vírus obrigatório não está disponível",
                         "O escaneamento de vírus é obrigatório mas o serviço não está disponível"));

        ERROR_MESSAGES.put(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED,
            new ErrorInfo("EXTENSAO_SUSPEITA_BLOQUEADA", "Extensão de arquivo suspeita foi bloqueada",
                         "Esta extensão de arquivo é considerada perigosa pela política de segurança"));

        ERROR_MESSAGES.put(FileErrorReason.QUOTA_EXCEEDED,
            new ErrorInfo("COTA_EXCEDIDA", "Cota de upload excedida",
                         "O limite de uploads para este cliente foi atingido"));

        // Códigos adicionais previamente não mapeados
        ERROR_MESSAGES.put(FileErrorReason.UNSUPPORTED_FILE_TYPE,
            new ErrorInfo("TIPO_NAO_SUPORTADO", "Tipo de arquivo não suportado",
                         "Este tipo de arquivo não é aceito pelo sistema"));

        ERROR_MESSAGES.put(FileErrorReason.INVALID_FILENAME,
            new ErrorInfo("NOME_ARQUIVO_INVALIDO", "Nome de arquivo inválido",
                         "O nome do arquivo contém caracteres proibidos ou é muito longo"));

        ERROR_MESSAGES.put(FileErrorReason.UPLOAD_TIMEOUT,
            new ErrorInfo("TEMPO_ESGOTADO_UPLOAD", "Tempo esgotado durante o upload",
                         "A operação demorou mais do que o permitido"));

        ERROR_MESSAGES.put(FileErrorReason.BULK_UPLOAD_TIMEOUT,
            new ErrorInfo("TEMPO_ESGOTADO_UPLOAD_LOTE", "Tempo esgotado no upload em lote",
                         "O processamento do lote excedeu o tempo limite configurado"));

        ERROR_MESSAGES.put(FileErrorReason.IO_ERROR,
            new ErrorInfo("ERRO_IO", "Erro de entrada/saída durante o processamento",
                         "Falha ao ler ou gravar o arquivo"));

        ERROR_MESSAGES.put(FileErrorReason.INSUFFICIENT_STORAGE,
            new ErrorInfo("ESPAÇO_INSUFICIENTE", "Espaço em disco insuficiente",
                         "Libere espaço ou ajuste a configuração de armazenamento"));

        ERROR_MESSAGES.put(FileErrorReason.BULK_UPLOAD_CANCELLED,
            new ErrorInfo("UPLOAD_LOTE_CANCELADO", "Upload em lote cancelado (fail-fast)",
                         "O lote foi interrompido após a primeira falha por configuração fail-fast"));

        ERROR_MESSAGES.put(FileErrorReason.USER_CANCELLED,
            new ErrorInfo("UPLOAD_CANCELADO_USUARIO", "Upload cancelado pelo usuário",
                         "A operação foi interrompida por solicitação do usuário"));

        ERROR_MESSAGES.put(FileErrorReason.BATCH_SIZE_EXCEEDED,
            new ErrorInfo("TAMANHO_LOTE_EXCEDIDO", "Quantidade de arquivos por lote excedida",
                         "Reduza o número de arquivos ou ajuste o limite máximo permitido"));
    }

    private static void initializeGenericErrorMessages() {
        GENERIC_ERROR_MESSAGES.put("RATE_LIMIT_EXCEEDED",
            new ErrorInfo("LIMITE_TAXA_EXCEDIDO", "Limite de taxa de upload excedido",
                         "Aguarde alguns minutos antes de tentar novamente"));

        GENERIC_ERROR_MESSAGES.put("QUOTA_EXCEEDED",
            new ErrorInfo("COTA_EXCEDIDA", "Cota de upload excedida",
                         "O limite de uploads para este cliente foi atingido"));

        GENERIC_ERROR_MESSAGES.put("INVALID_JSON_OPTIONS",
            new ErrorInfo("OPCOES_JSON_INVALIDAS", "Formato JSON inválido para opções de upload",
                         "Verifique a sintaxe do JSON enviado"));

        GENERIC_ERROR_MESSAGES.put("EMPTY_FILENAME",
            new ErrorInfo("NOME_ARQUIVO_VAZIO", "O nome do arquivo é obrigatório",
                         "Certifique-se de que o arquivo possui um nome válido"));

        GENERIC_ERROR_MESSAGES.put("INTERNAL_ERROR",
            new ErrorInfo("ERRO_INTERNO", "Erro interno durante o processamento",
                         "Contacte o suporte técnico se o problema persistir"));

        GENERIC_ERROR_MESSAGES.put("FAILED_TO_ANALYZE",
            new ErrorInfo("FALHA_ANALISE_ARQUIVO", "Falha ao analisar o conteúdo do arquivo",
                         "O arquivo pode estar corrompido ou em formato não suportado"));

        GENERIC_ERROR_MESSAGES.put("SYSTEM_POLICY_BLOCKED",
            new ErrorInfo("BLOQUEADO_POLITICA_SISTEMA", "Arquivo bloqueado pela política do sistema",
                         "Este tipo de arquivo não é permitido pela configuração de segurança"));

        GENERIC_ERROR_MESSAGES.put("VIRUS_SCAN_FAILED",
            new ErrorInfo("FALHA_ESCANEAMENTO_VIRUS", "Falha no escaneamento de vírus",
                         "Não foi possível verificar se o arquivo contém vírus"));

        GENERIC_ERROR_MESSAGES.put("STRUCTURE_VALIDATION_FAILED",
            new ErrorInfo("FALHA_VALIDACAO_ESTRUTURA", "Falha na validação da estrutura do arquivo",
                         "A estrutura interna do arquivo apresenta problemas"));

        GENERIC_ERROR_MESSAGES.put("INVALID_FILE_SIZE_CONFIG",
            new ErrorInfo("CONFIGURACAO_TAMANHO_INVALIDA", "Configuração de tamanho máximo inválida",
                         "Erro na configuração do sistema - contacte o administrador"));

        GENERIC_ERROR_MESSAGES.put("FILE_STORAGE_FAILED",
            new ErrorInfo("FALHA_ARMAZENAMENTO", "Falha ao armazenar o arquivo",
                         "Erro no sistema de armazenamento - tente novamente"));

        GENERIC_ERROR_MESSAGES.put("CONFLICT_POLICY_ERROR",
            new ErrorInfo("ERRO_POLITICA_CONFLITO", "Erro na política de conflito de nomes",
                         "Configure uma política adequada para lidar com arquivos duplicados"));

        GENERIC_ERROR_MESSAGES.put("CONFLICT_POLICY_SKIP",
            new ErrorInfo("POLITICA_CONFLITO_IGNORAR", "Arquivo ignorado devido à política de conflito",
                         "O arquivo já existe e a política está configurada para ignorar"));

        // Códigos de erro adicionais referenciados pelo FileServiceMessageAdapter
        GENERIC_ERROR_MESSAGES.put("CONFIGURATION_ERROR",
            new ErrorInfo("ERRO_CONFIGURACAO", "Erro na configuração do sistema",
                         "Há um problema na configuração do sistema de upload"));

        GENERIC_ERROR_MESSAGES.put("FILE_ANALYSIS_ERROR",
            new ErrorInfo("ERRO_ANALISE_ARQUIVO", "Erro na análise do arquivo",
                         "Não foi possível analisar completamente o arquivo enviado"));

        GENERIC_ERROR_MESSAGES.put("VIRUS_SCAN_ERROR",
            new ErrorInfo("ERRO_ESCANEAMENTO_VIRUS", "Erro no escaneamento de vírus",
                         "Falha técnica durante o processo de escaneamento de vírus"));

        GENERIC_ERROR_MESSAGES.put("FILE_STRUCTURE_ERROR",
            new ErrorInfo("ERRO_ESTRUTURA_ARQUIVO", "Erro na estrutura do arquivo",
                         "A estrutura interna do arquivo apresenta inconsistências"));

        GENERIC_ERROR_MESSAGES.put("FILE_STORE_ERROR",
            new ErrorInfo("ERRO_ARMAZENAMENTO_ARQUIVO", "Erro ao armazenar arquivo",
                         "Falha técnica durante o armazenamento do arquivo no sistema"));
    }

    /**
     * Obtém mensagem padronizada para um FileErrorReason
     */
    public static ErrorInfo getErrorInfo(FileErrorReason errorReason) {
        return ERROR_MESSAGES.getOrDefault(errorReason, ERROR_MESSAGES.get(FileErrorReason.UNKNOWN_ERROR));
    }

    /**
     * Obtém mensagem padronizada genérica
     */
    public static ErrorInfo getGenericErrorInfo(String errorKey) {
        return GENERIC_ERROR_MESSAGES.getOrDefault(errorKey,
            new ErrorInfo("ERRO_DESCONHECIDO", "Erro interno do sistema",
                         "Contacte o suporte técnico para assistência"));
    }

    /**
     * Retorna um mapa imutável contendo todas as mensagens de erro conhecidas,
     * combinando códigos específicos ({@link FileErrorReason}) e genéricos.
     * O mapa resultante usa o código do erro como chave e a mensagem amigável
     * em português como valor.
     */
    public static Map<String, String> getAllMessages() {
        Map<String, String> messages = new HashMap<>();
        ERROR_MESSAGES.forEach((reason, info) -> messages.put(reason.name(), info.getMessage()));
        GENERIC_ERROR_MESSAGES.forEach((code, info) -> messages.put(code, info.getMessage()));
        return Map.copyOf(messages);
    }

    /**
     * Cria uma resposta de erro padronizada genérica
     */
    public static Map<String, Object> createGenericErrorResponse(String errorCode, String message, String details) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", errorCode);
        response.put("message", message);
        response.put("details", details);
        response.put("timestamp", Instant.now().toString());
        return response;
    }


    /**
     * Converte FileUploadResultRecord para formato padronizado
     */
    public static Map<String, Object> convertFileUploadResultRecordToStandardFormat(FileUploadResultRecord result) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("timestamp", Instant.now().toString());

        if (result.success()) {
            response.put("data", createSuccessDataFromRecord(result));
        } else {
            ErrorInfo errorInfo = getErrorInfo(result.errorReason());
            response.put("code", errorInfo.getCode());
            response.put("message", errorInfo.getMessage());
            response.put("details", errorInfo.getDetails());

            Map<String, Object> errorData = new HashMap<>();
            if (result.originalFilename() != null) {
                errorData.put("originalFilename", result.originalFilename());
            }
            if (result.fileSize() >= 0) {
                errorData.put("fileSize", result.fileSize());
            }
            if (result.mimeType() != null) {
                errorData.put("mimeType", result.mimeType());
            }
            response.put("data", errorData);
        }

        return response;
    }

    /**
     * Cria uma resposta de erro padronizada
     */
    public static Map<String, Object> createStandardErrorResponse(FileErrorReason errorReason, String originalFilename, Long fileSize) {
        ErrorInfo errorInfo = getErrorInfo(errorReason);

        Map<String, Object> response = new HashMap<>();
        response.put("code", errorInfo.getCode());
        response.put("message", errorInfo.getMessage());
        response.put("details", errorInfo.getDetails());
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> errorData = new HashMap<>();
        if (originalFilename != null) {
            errorData.put("originalFilename", originalFilename);
        }
        if (fileSize != null) {
            errorData.put("fileSize", fileSize);
        }
        response.put("data", errorData);

        return response;
    }

    /**
     * Cria uma resposta de erro padronizada usando chave genérica
     */
    public static Map<String, Object> createGenericErrorResponseByKey(String errorKey, String originalFilename, Long fileSize) {
        ErrorInfo errorInfo = getGenericErrorInfo(errorKey);

        Map<String, Object> response = new HashMap<>();
        response.put("code", errorInfo.getCode());
        response.put("message", errorInfo.getMessage());
        response.put("details", errorInfo.getDetails());
        response.put("timestamp", Instant.now().toString());

        Map<String, Object> errorData = new HashMap<>();
        if (originalFilename != null) {
            errorData.put("originalFilename", originalFilename);
        }
        if (fileSize != null) {
            errorData.put("fileSize", fileSize);
        }
        response.put("data", errorData);

        return response;
    }

    /**
     * Formata mensagem de tamanho de arquivo excedido
     */
    public static Map<String, Object> createFileSizeExceededResponse(long fileSize, long maxSizeMb, String originalFilename) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "ARQUIVO_MUITO_GRANDE");
        response.put("message", "Arquivo excede o tamanho máximo permitido");
        response.put("details", String.format("Tamanho do arquivo: %d bytes. Tamanho máximo: %d MB", fileSize, maxSizeMb));
        response.put("timestamp", Instant.now().toString());
        response.put("originalFilename", originalFilename);
        response.put("fileSize", fileSize);
        response.put("maxSizeMb", maxSizeMb);

        return response;
    }

    /**
     * Formata mensagem de malware detectado
     */
    public static Map<String, Object> createMalwareDetectedResponse(String virusName, String originalFilename, Long fileSize) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", "MALWARE_DETECTADO");
        response.put("message", "Malware detectado no arquivo");
        response.put("details", "Vírus identificado: " + (virusName != null ? virusName : "Desconhecido"));
        response.put("timestamp", Instant.now().toString());
        response.put("originalFilename", originalFilename);
        response.put("virusName", virusName);

        if (fileSize != null) {
            response.put("fileSize", fileSize);
        }

        return response;
    }


    private static Map<String, Object> createSuccessDataFromRecord(FileUploadResultRecord result) {
        Map<String, Object> data = new HashMap<>();
        data.put("originalFilename", result.originalFilename());
        data.put("serverFilename", result.serverFilename());
        data.put("fileId", result.fileId());
        data.put("fileSize", result.fileSize());
        data.put("mimeType", result.mimeType());
        if (result.uploadTimestamp() != null) {
            data.put("uploadTimestamp", result.uploadTimestamp().toString());
        }
        return data;
    }

    /**
     * Classe interna para informações de erro
     */
    public static class ErrorInfo {
        private final String code;
        private final String message;
        private final String details;

        public ErrorInfo(String code, String message, String details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }
    }
}
