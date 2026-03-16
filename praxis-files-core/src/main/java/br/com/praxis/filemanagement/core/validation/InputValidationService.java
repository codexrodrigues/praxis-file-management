package br.com.praxis.filemanagement.core.validation;

import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Serviço responsável por validação robusta de entrada.
 * Centraliza todas as validações de parâmetros e entrada de dados.
 *
 * @since 1.0.0
 * @author Claude Code Refactoring
 */
@Service
public class InputValidationService {

    private static final Logger logger = LoggerFactory.getLogger(InputValidationService.class);

    // Constantes de validação
    private static final long MAX_FILENAME_LENGTH = 255;
    private static final long MIN_FILE_SIZE = 1;
    private static final long MAX_FILE_SIZE_DEFAULT = 100 * 1024 * 1024; // 100MB
    private static final int MAX_MIME_TYPE_LENGTH = 100;
    private static final int MAX_PATH_LENGTH = 4096;

    // Padrões de validação
    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern SUSPICIOUS_FILENAME_PATTERN = Pattern.compile("(?i)(\\.\\.|%2e%2e|%252e%252e|\\.exe|\\.bat|\\.cmd|\\.scr|\\.vbs|\\.sh|\\.ps1|\\.py|\\.php)");
    private static final Pattern VALID_MIME_TYPE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9][a-zA-Z0-9!#$&\\-\\^_]*\\/[a-zA-Z0-9][a-zA-Z0-9!#$&\\-\\^_.]*$");

    // Extensões perigosas
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "com", "scr", "pif", "vbs", "js", "jar", "app", "deb", "pkg", "dmg",
        "sh", "bash", "zsh", "fish", "ps1", "py", "rb", "pl", "php", "jsp", "asp", "aspx"
    );

    /**
     * Resultado da validação de entrada.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;

        public ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = errors != null ? List.copyOf(errors) : List.of();
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }

        public static ValidationResult failure(ValidationError error) {
            return new ValidationResult(false, List.of(error));
        }

        public boolean isValid() {
            return valid;
        }

        public List<ValidationError> getErrors() {
            return errors;
        }

        public String getErrorSummary() {
            return errors.stream()
                        .map(ValidationError::getMessage)
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Validation failed");
        }

        public FileErrorReason getPrimaryErrorReason() {
            return errors.isEmpty() ? FileErrorReason.INVALID_TYPE : errors.get(0).getErrorReason();
        }
    }

    /**
     * Erro de validação específico.
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        private final FileErrorReason errorReason;
        private final Object invalidValue;

        public ValidationError(String field, String message, FileErrorReason errorReason, Object invalidValue) {
            this.field = field;
            this.message = message;
            this.errorReason = errorReason;
            this.invalidValue = invalidValue;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }

        public FileErrorReason getErrorReason() {
            return errorReason;
        }

        public Object getInvalidValue() {
            return invalidValue;
        }
    }


    /**
     * Realiza validação abrangente e robusta de arquivo de upload.
     *
     * <p>Este método é o ponto central de validação de entrada, aplicando múltiplas camadas de verificação:
     * <ul>
     *   <li><strong>Validação Básica</strong>: Arquivo null, vazio, tamanho mínimo, nome original</li>
     *   <li><strong>Validação de Nome</strong>: Comprimento, caracteres válidos, extensões perigosas</li>
     *   <li><strong>Validação de Opções</strong>: Parâmetros de configuração, MIME types, tamanhos</li>
     *   <li><strong>Validação de Segurança</strong>: Path traversal, caracteres de controle, incompatibilidades MIME</li>
     * </ul>
     *
     * <p><strong>Modos de Validação:</strong>
     * <ul>
     *   <li><strong>Modo Padrão</strong>: Aplica validações essenciais de segurança</li>
     *   <li><strong>Modo Strict (strictValidation=true)</strong>: Aplica todas as validações rigorosas incluindo:
     *     <ul>
     *       <li>Verificação de caracteres válidos em nomes</li>
     *       <li>Bloqueio de extensões perigosas</li>
     *       <li>Validação de nomes reservados do Windows</li>
     *       <li>Detecção de incompatibilidade MIME vs extensão</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><strong>Validações Sempre Aplicadas (Críticas para Segurança):</strong>
     * <ul>
     *   <li>Detecção de path traversal (.., ./, \\)</li>
     *   <li>Detecção de padrões suspeitos em nomes</li>
     *   <li>Validação de tamanhos e parâmetros básicos</li>
     * </ul>
     *
     * <p><strong>Integração com Sistema de Mensagens:</strong>
     * Cada erro de validação é mapeado para FileErrorReason apropriado, permitindo
     * mensagens padronizadas via FileServiceMessageAdapter.
     *
     * @param file Arquivo multipart a ser validado (pode ser null - será reportado como erro)
     * @param options Opções de configuração do upload (pode ser null para usar padrões).
     *                Propriedades importantes:
     *                <ul>
     *                  <li><strong>strictValidation</strong>: Habilita validações rigorosas</li>
     *                  <li><strong>maxUploadSizeMb</strong>: Limite de tamanho por arquivo</li>
     *                  <li><strong>acceptMimeTypes</strong>: Lista de MIME types permitidos</li>
     *                </ul>
     * @return ValidationResult contendo:
     *         <ul>
     *           <li><strong>Sucesso</strong>: isValid()=true, getErrors() vazia</li>
     *           <li><strong>Falha</strong>: isValid()=false, getErrors() com lista de ValidationError</li>
     *           <li><strong>Utilitários</strong>: getErrorSummary() para mensagem consolidada, getPrimaryErrorReason() para primeiro erro</li>
     *         </ul>
     * @since 1.0.0
     * @see ValidationResult
     * @see ValidationError
     * @see FileUploadOptionsRecord
     */
    public ValidationResult validateUploadFile(MultipartFile file, FileUploadOptionsRecord options) {
        List<ValidationError> errors = new ArrayList<>();

        // Validação básica do arquivo
        errors.addAll(validateBasicFile(file));

        // Validação do nome do arquivo
        if (file != null && file.getOriginalFilename() != null) {
            errors.addAll(validateFilename(file.getOriginalFilename(), options));
        }

        // Validação das opções
        if (options != null) {
            errors.addAll(validateUploadOptionsRecord(options));
        }

        // Validação de segurança
        if (file != null) {
            errors.addAll(validateSecurityConstraints(file, options));
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Valida parâmetros básicos do arquivo.
     */
    private List<ValidationError> validateBasicFile(MultipartFile file) {
        List<ValidationError> errors = new ArrayList<>();

        if (file == null) {
            errors.add(new ValidationError("file", "Arquivo não pode ser null",
                                         FileErrorReason.EMPTY_FILE, null));
            return errors;
        }

        // Verificar se arquivo não está vazio
        if (file.isEmpty()) {
            errors.add(new ValidationError("file", "Arquivo não pode estar vazio",
                                         FileErrorReason.EMPTY_FILE, file.getSize()));
        }

        // Verificar tamanho mínimo
        if (file.getSize() < MIN_FILE_SIZE) {
            errors.add(new ValidationError("file.size", "Arquivo muito pequeno (mínimo: " + MIN_FILE_SIZE + " bytes)",
                                         FileErrorReason.EMPTY_FILE, file.getSize()));
        }

        // Verificar se tem nome original
        if (file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            errors.add(new ValidationError("file.originalFilename", "Nome do arquivo é obrigatório",
                                         FileErrorReason.INVALID_TYPE, file.getOriginalFilename()));
        }

        // Verificar MIME type básico
        String contentType = file.getContentType();
        if (contentType != null && contentType.length() > MAX_MIME_TYPE_LENGTH) {
            errors.add(new ValidationError("file.contentType", "MIME type muito longo",
                                         FileErrorReason.INVALID_TYPE, contentType));
        }

        return errors;
    }

    /**
     * Valida nome do arquivo de forma rigorosa.
     */
    private List<ValidationError> validateFilename(String filename, FileUploadOptionsRecord options) {
        List<ValidationError> errors = new ArrayList<>();

        if (filename == null || filename.trim().isEmpty()) {
            errors.add(new ValidationError("filename", "Nome do arquivo não pode ser vazio",
                                         FileErrorReason.INVALID_TYPE, filename));
            return errors;
        }

        // Verificar comprimento
        if (filename.length() > MAX_FILENAME_LENGTH) {
            errors.add(new ValidationError("filename", "Nome do arquivo muito longo (máximo: " + MAX_FILENAME_LENGTH + " caracteres)",
                                         FileErrorReason.INVALID_TYPE, filename.length()));
        }

        filename = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFKC);

        if (!VALID_FILENAME_PATTERN.matcher(filename).matches()) {
            errors.add(new ValidationError("filename", "Nome do arquivo contém caracteres inválidos",
                                         FileErrorReason.INVALID_TYPE, filename));
        }

        if (isWindowsReservedName(filename)) {
            errors.add(new ValidationError("filename", "Nome de arquivo reservado do sistema",
                                         FileErrorReason.INVALID_TYPE, filename));
        }

        boolean isStrictMode = options != null && options.strictValidation();

        // Verificações adicionais aplicadas apenas em modo strict
        if (isStrictMode) {
            // Verificar extensão perigosa
            String extension = extractExtension(filename);
            if (DANGEROUS_EXTENSIONS.contains(extension.toLowerCase())) {
                errors.add(new ValidationError("filename.extension", "Extensão de arquivo não permitida: " + extension,
                                             FileErrorReason.INVALID_TYPE, extension));
            }
        }

        // Verificar padrões suspeitos sempre (sempre crítico para segurança)
        if (SUSPICIOUS_FILENAME_PATTERN.matcher(filename).find()) {
            errors.add(new ValidationError("filename", "Nome do arquivo contém padrões suspeitos",
                                         FileErrorReason.SECURITY_VIOLATION, filename));
        }

        return errors;
    }


    /**
     * Valida opções de upload usando Records.
     */
    private List<ValidationError> validateUploadOptionsRecord(FileUploadOptionsRecord options) {
        List<ValidationError> errors = new ArrayList<>();

        // Validar política de conflito
        NameConflictPolicy policy = options.nameConflictPolicy();
        if (policy == null) {
            // Não é erro - vai usar padrão
            logger.debug("NameConflictPolicy not specified, will use default");
        }

        // Validar tamanho máximo: null ou <=0 significa 'usar default do servidor'
        Long maxSize = options.maxUploadSizeMb();
        if (maxSize != null) {
            if (maxSize <= 0) {
                // Sem erro - apenas log informativo
                logger.debug("maxUploadSizeMb <= 0 informado, será aplicado default do servidor");
            } else if (maxSize > 1000) { // 1GB como limite absoluto
                errors.add(new ValidationError("options.maxUploadSizeMb", "Tamanho máximo muito grande (máximo: 1000MB)",
                                             FileErrorReason.FILE_TOO_LARGE, maxSize));
            }
        }

        // Validar tipos MIME aceitos
        if (options.acceptMimeTypes() != null) {
            for (String mimeType : options.acceptMimeTypes()) {
                if (mimeType != null && !VALID_MIME_TYPE_PATTERN.matcher(mimeType).matches()) {
                    errors.add(new ValidationError("options.acceptMimeTypes", "MIME type inválido: " + mimeType,
                                                 FileErrorReason.INVALID_TYPE, mimeType));
                }
            }
        }

        return errors;
    }

    /**
     * Valida restrições de segurança.
     */
    private List<ValidationError> validateSecurityConstraints(MultipartFile file, FileUploadOptionsRecord options) {
        List<ValidationError> errors = new ArrayList<>();

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return errors;
        }

        filename = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFKC);

        // Verificações básicas sempre aplicadas (path traversal é sempre perigoso)
        if (filename.contains("..") || filename.contains("./") || filename.contains("\\")) {
            errors.add(new ValidationError("filename", "Tentativa de path traversal detectada",
                                         FileErrorReason.SECURITY_VIOLATION, filename));
        }

        // Verificações adicionais aplicadas apenas se strictValidation estiver habilitado
        boolean isStrictMode = options != null && options.strictValidation();

        if (isStrictMode) {
            // Verificar caracteres de controle
            for (char c : filename.toCharArray()) {
                if (Character.isISOControl(c)) {
                    errors.add(new ValidationError("filename", "Caracteres de controle não permitidos no nome do arquivo",
                                                 FileErrorReason.SECURITY_VIOLATION, filename));
                    break;
                }
            }

            // Verificar MIME type suspeito vs extensão
            String contentType = file.getContentType();
            String extension = extractExtension(filename);
            if (contentType != null && isMimeExtensionMismatch(contentType, extension)) {
                logger.warn("Suspicious MIME type mismatch detected: {} vs {}", contentType, extension);
                errors.add(new ValidationError("filename",
                    "Incompatibilidade suspeita entre tipo MIME (" + contentType + ") e extensão (" + extension + ")",
                    FileErrorReason.SECURITY_VIOLATION, filename));
            }
        }

        return errors;
    }

    /**
     * Valida path de diretório para operações de armazenamento seguro.
     *
     * <p>Este método aplica validações rigorosas de segurança em paths de diretório:
     * <ul>
     *   <li><strong>Validação Básica</strong>: Path não-nulo, não-vazio, comprimento máximo</li>
     *   <li><strong>Segurança</strong>: Detecção de path traversal (..) e caracteres suspeitos (~)</li>
     *   <li><strong>Formato</strong>: Validação de sintaxe usando java.nio.file.Paths</li>
     *   <li><strong>Tipo</strong>: Verifica se path é absoluto (requerido para segurança)</li>
     * </ul>
     *
     * <p><strong>Restrições de Segurança:</strong>
     * <ul>
     *   <li>Path deve ser absoluto (iniciando com / no Unix ou C:\\ no Windows)</li>
     *   <li>Não pode conter ".." (path traversal)</li>
     *   <li>Não pode conter "~" (expansão de home directory)</li>
     *   <li>Comprimento máximo de 4096 caracteres</li>
     * </ul>
     *
     * <p><strong>Uso Típico:</strong>
     * Usado internamente pelo sistema antes de criar diretórios ou resolver paths
     * de destino para uploads, garantindo que operações de arquivo permaneçam dentro
     * de limites seguros.
     *
     * @param path String representando o path do diretório (deve ser absoluto)
     * @return ValidationResult contendo:
     *         <ul>
     *           <li><strong>Sucesso</strong>: Path é válido e seguro para uso</li>
     *           <li><strong>Falha</strong>: Lista de erros com detalhes sobre problemas encontrados</li>
     *         </ul>
     * @since 1.0.0
     * @see java.nio.file.Paths
     * @see ValidationResult
     */
    public ValidationResult validateDirectoryPath(String path) {
        List<ValidationError> errors = new ArrayList<>();

        if (path == null || path.trim().isEmpty()) {
            errors.add(new ValidationError("path", "Path não pode ser vazio",
                                         FileErrorReason.INVALID_PATH, path));
            return ValidationResult.failure(errors);
        }

        if (path.length() > MAX_PATH_LENGTH) {
            errors.add(new ValidationError("path", "Path muito longo",
                                         FileErrorReason.INVALID_PATH, path.length()));
        }

        // Verificar caracteres suspeitos
        if (path.contains("..") || path.contains("~")) {
            errors.add(new ValidationError("path", "Path contém caracteres suspeitos",
                                         FileErrorReason.SECURITY_VIOLATION, path));
        }

        // Tentar criar Path para validar formato
        try {
            Path pathObj = Paths.get(path);
            if (!pathObj.isAbsolute()) {
                errors.add(new ValidationError("path", "Path deve ser absoluto",
                                             FileErrorReason.INVALID_PATH, path));
            }
        } catch (Exception e) {
            errors.add(new ValidationError("path", "Path inválido: " + e.getMessage(),
                                         FileErrorReason.INVALID_PATH, path));
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Extrai extensão do arquivo.
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * Verifica se é nome reservado do Windows.
     */
    private boolean isWindowsReservedName(String filename) {
        String baseName = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        String[] reserved = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
                           "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5",
                           "LPT6", "LPT7", "LPT8", "LPT9"};

        for (String reservedName : reserved) {
            if (reservedName.equalsIgnoreCase(baseName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica incompatibilidade suspeita entre MIME type e extensão.
     * Implementação abrangente para detectar tentativas de bypass de segurança.
     */
    private boolean isMimeExtensionMismatch(String mimeType, String extension) {
        if (mimeType == null || extension == null) {
            return false;
        }

        String lowerExtension = extension.toLowerCase();

        // Verificações críticas de segurança - MIME types perigosos
        if (isDangerousMimeType(mimeType)) {
            logger.warn("Dangerous MIME type detected: {} with extension: {}", mimeType, extension);
            return true;
        }

        // Verificações de consistência específicas
        switch (mimeType.toLowerCase()) {
            // Imagens
            case "image/jpeg":
                return !("jpg".equals(lowerExtension) || "jpeg".equals(lowerExtension));
            case "image/png":
                return !"png".equals(lowerExtension);
            case "image/gif":
                return !"gif".equals(lowerExtension);
            case "image/webp":
                return !"webp".equals(lowerExtension);
            case "image/bmp":
                return !"bmp".equals(lowerExtension);
            case "image/tiff":
                return !("tiff".equals(lowerExtension) || "tif".equals(lowerExtension));

            // Documentos
            case "application/pdf":
                return !"pdf".equals(lowerExtension);
            case "application/msword":
                return !"doc".equals(lowerExtension);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return !"docx".equals(lowerExtension);
            case "application/vnd.ms-excel":
                return !"xls".equals(lowerExtension);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return !"xlsx".equals(lowerExtension);
            case "application/vnd.ms-powerpoint":
                return !"ppt".equals(lowerExtension);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                return !"pptx".equals(lowerExtension);

            // Texto
            case "text/plain":
                return !"txt".equals(lowerExtension);
            case "text/csv":
                return !"csv".equals(lowerExtension);
            case "text/html":
                return !("html".equals(lowerExtension) || "htm".equals(lowerExtension));
            case "text/css":
                return !"css".equals(lowerExtension);
            case "application/json":
                return !"json".equals(lowerExtension);
            case "application/xml":
            case "text/xml":
                return !"xml".equals(lowerExtension);

            // Arquivos compactados
            case "application/zip":
                return !"zip".equals(lowerExtension);
            case "application/x-rar-compressed":
            case "application/vnd.rar":
                return !"rar".equals(lowerExtension);
            case "application/x-7z-compressed":
                return !"7z".equals(lowerExtension);
            case "application/gzip":
                return !("gz".equals(lowerExtension) || "gzip".equals(lowerExtension));
            case "application/x-tar":
                return !"tar".equals(lowerExtension);

            // Áudio
            case "audio/mpeg":
                return !("mp3".equals(lowerExtension) || "mpeg".equals(lowerExtension));
            case "audio/wav":
                return !"wav".equals(lowerExtension);
            case "audio/ogg":
                return !"ogg".equals(lowerExtension);

            // Vídeo
            case "video/mp4":
                return !"mp4".equals(lowerExtension);
            case "video/avi":
                return !"avi".equals(lowerExtension);
            case "video/quicktime":
                return !"mov".equals(lowerExtension);

            default:
                // Para MIME types não mapeados, verificar se a extensão está na lista de perigosas
                if (DANGEROUS_EXTENSIONS.contains(lowerExtension)) {
                    logger.warn("Dangerous extension {} detected with MIME type: {}", extension, mimeType);
                    return true;
                }
                return false;
        }
    }

    /**
     * Verifica se um MIME type é considerado perigoso e deve ser bloqueado.
     */
    private boolean isDangerousMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        String lowerMimeType = mimeType.toLowerCase();

        // MIME types explicitamente perigosos
        boolean isDangerous = lowerMimeType.startsWith("application/x-executable") ||
                              lowerMimeType.startsWith("application/x-dosexec") ||
                              lowerMimeType.startsWith("application/x-msdownload") ||
                              lowerMimeType.startsWith("application/x-msdos-program") ||
                              lowerMimeType.startsWith("application/x-winexe") ||
                              lowerMimeType.startsWith("application/x-sh") ||
                              lowerMimeType.startsWith("application/x-shellscript") ||
                              lowerMimeType.startsWith("text/x-shellscript") ||
                              lowerMimeType.startsWith("application/x-php") ||
                              lowerMimeType.startsWith("text/x-php") ||
                              lowerMimeType.startsWith("application/x-python") ||
                              lowerMimeType.startsWith("text/x-python") ||
                              lowerMimeType.startsWith("application/javascript") ||
                              lowerMimeType.startsWith("text/javascript") ||
                              lowerMimeType.startsWith("application/x-javascript") ||
                              lowerMimeType.startsWith("application/bat") ||
                              lowerMimeType.startsWith("application/x-bat") ||
                              lowerMimeType.startsWith("application/cmd") ||
                              lowerMimeType.equals("application/octet-stream"); // Genérico suspeito

        return isDangerous;
    }

    /**
     * Valida parâmetros de entrada para métodos críticos.
     */
    public static void requireNonNull(Object obj, String paramName) {
        if (obj == null) {
            throw new IllegalArgumentException(paramName + " não pode ser null");
        }
    }

    /**
     * Valida string não-vazia.
     */
    public static void requireNonEmpty(String str, String paramName) {
        requireNonNull(str, paramName);
        if (str.trim().isEmpty()) {
            throw new IllegalArgumentException(paramName + " não pode ser vazio");
        }
    }

    /**
     * Valida número positivo.
     */
    public static void requirePositive(long number, String paramName) {
        if (number <= 0) {
            throw new IllegalArgumentException(paramName + " deve ser positivo, recebido: " + number);
        }
    }
}
