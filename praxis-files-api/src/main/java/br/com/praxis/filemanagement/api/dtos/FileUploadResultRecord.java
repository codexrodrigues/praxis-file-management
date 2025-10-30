package br.com.praxis.filemanagement.api.dtos;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Map;

/**
 * Record para resultado de upload de arquivo com documentação OpenAPI completa.
 */
@Schema(description = "Resultado do upload de arquivo com informações detalhadas")
public record FileUploadResultRecord(
    
    @Schema(
        description = "Nome original do arquivo enviado",
        example = "documento.pdf",
        required = true
    )
    @NotBlank(message = "Nome do arquivo original é obrigatório")
    String originalFilename,
    
    @Schema(
        description = "Nome do arquivo salvo no servidor",
        example = "doc_a1b2c3d4.pdf",
        nullable = true
    )
    String serverFilename,
    
    @Schema(
        description = "Identificador único do arquivo (UUID)",
        example = "550e8400-e29b-41d4-a716-446655440000",
        nullable = true
    )
    String fileId,
    
    @Schema(
        description = "Tamanho do arquivo em bytes",
        example = "1048576",
        minimum = "0"
    )
    @Min(value = 0, message = "Tamanho do arquivo não pode ser negativo")
    long fileSize,
    
    @Schema(
        description = "Tipo MIME detectado do arquivo",
        example = "application/pdf"
    )
    String mimeType,
    
    @Schema(
        description = "Indica se o upload foi bem-sucedido",
        example = "true",
        required = true
    )
    @NotNull(message = "Status de sucesso é obrigatório")
    Boolean success,
    
    @Schema(
        description = "Razão do erro caso o upload falhe",
        example = "ARQUIVO_MUITO_GRANDE",
        nullable = true
    )
    FileErrorReason errorReason,
    
    @Schema(
        description = "Mensagem de erro amigável",
        example = "Arquivo excede o tamanho máximo permitido",
        nullable = true
    )
    String errorMessage,
    
    @Schema(
        description = "Timestamp do upload",
        example = "2025-07-10T23:15:30.123Z",
        required = true
    )
    @NotNull(message = "Timestamp de upload é obrigatório")
    Instant uploadTimestamp,
    
    @Schema(
        description = "Metadados e informações adicionais",
        example = "{\"checksumMD5\": \"d41d8cd98f00b204e9800998ecf8427e\"}",
        nullable = true
    )
    Map<String, Object> metadata
    
) {
    
    /**
     * Compact constructor with validation
     */
    public FileUploadResultRecord {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Nome do arquivo original não pode ser vazio");
        }
        if (success == null) {
            throw new IllegalArgumentException("Status de sucesso é obrigatório");
        }
        if (uploadTimestamp == null) {
            uploadTimestamp = Instant.now();
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("Tamanho do arquivo não pode ser negativo");
        }
        // Make defensive copy
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
    
    /**
     * Factory method para resultado de sucesso
     */
    public static FileUploadResultRecord success(
            String originalFilename,
            String serverFilename,
            String fileId,
            long fileSize,
            String mimeType) {
        return new FileUploadResultRecord(
            originalFilename,
            serverFilename,
            fileId,
            fileSize,
            mimeType,
            true,
            null,
            null,
            Instant.now(),
            Map.of()
        );
    }
    
    /**
     * Factory method para resultado de erro
     */
    public static FileUploadResultRecord error(
            String originalFilename,
            FileErrorReason errorReason,
            String errorMessage) {
        return new FileUploadResultRecord(
            originalFilename,
            null,
            null,
            0L,
            null,
            false,
            errorReason,
            errorMessage,
            Instant.now(),
            Map.of()
        );
    }
    
    /**
     * Factory method para resultado de erro com tamanho de arquivo
     */
    public static FileUploadResultRecord error(
            String originalFilename,
            FileErrorReason errorReason,
            String errorMessage,
            long fileSize) {
        return new FileUploadResultRecord(
            originalFilename,
            null,
            null,
            fileSize,
            null,
            false,
            errorReason,
            errorMessage,
            Instant.now(),
            Map.of()
        );
    }
    
    /**
     * Builder pattern para criação fluente
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String originalFilename;
        private String serverFilename;
        private String fileId;
        private long fileSize;
        private String mimeType;
        private Boolean success;
        private FileErrorReason errorReason;
        private String errorMessage;
        private Instant uploadTimestamp = Instant.now();
        private Map<String, Object> metadata = Map.of();
        
        public Builder originalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
            return this;
        }
        
        public Builder serverFilename(String serverFilename) {
            this.serverFilename = serverFilename;
            return this;
        }
        
        public Builder fileId(String fileId) {
            this.fileId = fileId;
            return this;
        }
        
        public Builder fileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }
        
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }
        
        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder errorReason(FileErrorReason errorReason) {
            this.errorReason = errorReason;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder uploadTimestamp(Instant uploadTimestamp) {
            this.uploadTimestamp = uploadTimestamp;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public FileUploadResultRecord build() {
            return new FileUploadResultRecord(
                originalFilename,
                serverFilename,
                fileId,
                fileSize,
                mimeType,
                success,
                errorReason,
                errorMessage,
                uploadTimestamp,
                metadata
            );
        }
    }
}
