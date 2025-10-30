package br.com.praxis.filemanagement.api.dtos;

import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Modern Java Record for file upload options (Java 17+ feature)
 * Provides immutability, automatic equals/hashCode, and thread safety
 */
@Schema(description = "Opções de configuração para upload de arquivos")
public record FileUploadOptionsRecord(

    @Schema(
        description = "Lista de extensões de arquivo permitidas",
        example = "[\"pdf\", \"jpg\", \"png\", \"docx\"]",
        nullable = true
    )
    @Size(max = 20, message = "Máximo de 20 extensões permitidas")
    List<String> allowedExtensions,

    @Schema(
        description = "Lista de tipos MIME aceitos",
        example = "[\"application/pdf\", \"image/jpeg\", \"image/png\"]",
        nullable = true
    )
    @Size(max = 50, message = "Máximo de 50 tipos MIME")
    List<String> acceptMimeTypes,

    @Schema(
        description = "Política de resolução de conflito de nomes",
        example = "RENAME",
        defaultValue = "RENAME"
    )
    NameConflictPolicy nameConflictPolicy,

    @Schema(
        description = "Tamanho máximo do arquivo em MB",
        example = "50",
        minimum = "1",
        maximum = "500",
        nullable = true
    )
    // Bean Validation removida para permitir null e fallback server-side
    Long maxUploadSizeMb,

    @Schema(
        description = "Ativar validação rigorosa de arquivos",
        example = "false",
        defaultValue = "false"
    )
    boolean strictValidation,

    @Schema(
        description = "Diretório de destino relativo",
        example = "documents/invoices",
        nullable = true,
        maxLength = 255
    )
    @Size(max = 255, message = "Caminho do diretório muito longo")
    String targetDirectory,

    @Schema(
        description = "Forçar escaneamento de vírus",
        example = "true",
        defaultValue = "false"
    )
    boolean enableVirusScanning,

    @Schema(
        description = "Metadados personalizados para o arquivo",
        example = "{\"category\": \"invoice\", \"department\": \"finance\"}",
        nullable = true
    )
    Map<String, String> customMetadata,

    @Schema(
        description = "Ativar modo fail-fast para uploads em lote (para na primeira falha)",
        example = "false",
        defaultValue = "false"
    )
    boolean failFastMode
) {
    /**
     * Compact constructor with normalization and safe defaults
     */
    public FileUploadOptionsRecord {
        if (nameConflictPolicy == null) {
            nameConflictPolicy = NameConflictPolicy.RENAME;
        }
        // Defensive copies for mutable collections and null-safe defaults
        allowedExtensions = allowedExtensions != null ? List.copyOf(allowedExtensions) : List.of();
        acceptMimeTypes = acceptMimeTypes != null ? List.copyOf(acceptMimeTypes) : List.of();
        customMetadata = customMetadata != null ? Map.copyOf(customMetadata) : Map.of();
        // maxUploadSizeMb pode ser null (fallback aplicado no servidor)
    }

    /**
     * Static factory method for default options
     */
    public static FileUploadOptionsRecord defaultOptions() {
        return new FileUploadOptionsRecord(
            List.of(),
            List.of(),
            NameConflictPolicy.RENAME,
            100L,
            false, // strictValidation = false by default (can be enabled in production)
            null,
            false,
            Map.of(),
            false // failFastMode = false by default
        );
    }

    /**
     * Builder pattern for fluent creation
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> allowedExtensions = List.of();
        private List<String> acceptMimeTypes = List.of();
        private NameConflictPolicy nameConflictPolicy = NameConflictPolicy.RENAME;
        private Long maxUploadSizeMb = null; // deixar null para permitir fallback server-side
        private boolean strictValidation = true;
        private String targetDirectory;
        private boolean enableVirusScanning = false;
        private Map<String, String> customMetadata = Map.of();
        private boolean failFastMode = false;

        public Builder allowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
            return this;
        }

        public Builder acceptMimeTypes(List<String> acceptMimeTypes) {
            this.acceptMimeTypes = acceptMimeTypes;
            return this;
        }

        public Builder nameConflictPolicy(NameConflictPolicy policy) {
            this.nameConflictPolicy = policy;
            return this;
        }

        public Builder maxUploadSizeMb(Long maxUploadSizeMb) {
            this.maxUploadSizeMb = maxUploadSizeMb;
            return this;
        }

        public Builder strictValidation(boolean strictValidation) {
            this.strictValidation = strictValidation;
            return this;
        }

        public Builder targetDirectory(String targetDirectory) {
            this.targetDirectory = targetDirectory;
            return this;
        }

        public Builder enableVirusScanning(boolean enableVirusScanning) {
            this.enableVirusScanning = enableVirusScanning;
            return this;
        }

        public Builder customMetadata(Map<String, String> customMetadata) {
            this.customMetadata = customMetadata;
            return this;
        }

        public Builder failFastMode(boolean failFastMode) {
            this.failFastMode = failFastMode;
            return this;
        }

        public FileUploadOptionsRecord build() {
            return new FileUploadOptionsRecord(
                allowedExtensions,
                acceptMimeTypes,
                nameConflictPolicy,
                maxUploadSizeMb,
                strictValidation,
                targetDirectory,
                enableVirusScanning,
                customMetadata,
                failFastMode
            );
        }
    }
}
