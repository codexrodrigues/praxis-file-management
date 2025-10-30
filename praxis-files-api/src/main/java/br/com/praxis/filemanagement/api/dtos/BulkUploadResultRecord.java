package br.com.praxis.filemanagement.api.dtos;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Record para resultado de upload múltiplo de arquivos com estatísticas consolidadas.
 * 
 * @author ErgonX
 * @since 1.1.0
 */
@Schema(description = "Resultado do upload múltiplo de arquivos com estatísticas detalhadas")
public record BulkUploadResultRecord(
    
    @Schema(
        description = "Lista de resultados individuais para cada arquivo",
        required = true
    )
    @NotNull(message = "Lista de resultados é obrigatória")
    List<FileUploadResultRecord> results,
    
    @Schema(
        description = "Total de arquivos processados",
        example = "5",
        minimum = "0"
    )
    @Min(value = 0, message = "Total processado não pode ser negativo")
    int totalProcessed,
    
    @Schema(
        description = "Número de uploads bem-sucedidos",
        example = "3",
        minimum = "0"
    )
    @Min(value = 0, message = "Total de sucessos não pode ser negativo")
    int totalSuccess,
    
    @Schema(
        description = "Número de uploads que falharam",
        example = "2",
        minimum = "0"
    )
    @Min(value = 0, message = "Total de falhas não pode ser negativo")
    int totalFailed,
    
    @Schema(
        description = "Indica se a operação como um todo foi bem-sucedida (todos os arquivos)",
        example = "false"
    )
    @NotNull(message = "Status de sucesso geral é obrigatório")
    Boolean overallSuccess,
    
    @Schema(
        description = "Timestamp do início do processamento",
        example = "2025-07-21T20:15:30.123Z",
        required = true
    )
    @NotNull(message = "Timestamp de início é obrigatório")
    Instant startTimestamp,
    
    @Schema(
        description = "Timestamp do fim do processamento",
        example = "2025-07-21T20:15:35.456Z",
        required = true
    )
    @NotNull(message = "Timestamp de fim é obrigatório")
    Instant endTimestamp,
    
    @Schema(
        description = "Duração total do processamento em milissegundos",
        example = "5333",
        minimum = "0"
    )
    @Min(value = 0, message = "Duração não pode ser negativa")
    long processingTimeMs,
    
    @Schema(
        description = "Tamanho total de todos os arquivos processados em bytes",
        example = "10485760",
        minimum = "0"
    )
    @Min(value = 0, message = "Tamanho total não pode ser negativo")
    long totalSizeBytes,
    
    @Schema(
        description = "Metadados adicionais da operação",
        example = "{\"processedInParallel\": true, \"maxConcurrency\": 3}",
        nullable = true
    )
    Map<String, Object> metadata,
    
    @Schema(
        description = "Número de arquivos cancelados devido ao fail-fast mode",
        example = "2",
        minimum = "0"
    )
    @Min(value = 0, message = "Total cancelado não pode ser negativo")
    int totalCancelled,
    
    @Schema(
        description = "Indica se a operação foi interrompida pelo fail-fast mode",
        example = "true"
    )
    boolean wasFailFastTriggered
    
) {
    
    /**
     * Compact constructor with validation
     */
    public BulkUploadResultRecord {
        if (results == null) {
            throw new IllegalArgumentException("Lista de resultados é obrigatória");
        }
        if (startTimestamp == null) {
            startTimestamp = Instant.now();
        }
        if (endTimestamp == null) {
            endTimestamp = Instant.now();
        }
        if (overallSuccess == null) {
            throw new IllegalArgumentException("Status de sucesso geral é obrigatório");
        }
        if (processingTimeMs < 0) {
            processingTimeMs = endTimestamp.toEpochMilli() - startTimestamp.toEpochMilli();
        }
        
        // Make defensive copies
        results = List.copyOf(results);
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        
        // Auto-calculate statistics if not provided
        if (totalProcessed == 0) {
            totalProcessed = results.size();
        }
        // Only auto-calculate if both are 0 to avoid overriding partial data
        if (totalSuccess == 0 && totalFailed == 0) {
            totalSuccess = (int) results.stream().filter(FileUploadResultRecord::success).count();
            totalFailed = totalProcessed - totalSuccess;
        } else if (totalSuccess > 0 && totalFailed == 0) {
            // If only success is set, calculate failed
            totalFailed = totalProcessed - totalSuccess;
        } else if (totalFailed > 0 && totalSuccess == 0) {
            // If only failed is set, calculate success
            totalSuccess = totalProcessed - totalFailed;
        }
        if (overallSuccess == null) {
            overallSuccess = totalFailed == 0 && totalProcessed > 0;
        }
        if (totalSizeBytes == 0) {
            totalSizeBytes = results.stream().mapToLong(FileUploadResultRecord::fileSize).sum();
        }
    }
    
    /**
     * Factory method para criar resultado baseado em lista de resultados individuais
     */
    public static BulkUploadResultRecord fromResults(List<FileUploadResultRecord> results) {
        Instant now = Instant.now();
        return fromResults(results, now, now);
    }
    
    /**
     * Factory method para criar resultado com timestamps específicos
     */
    public static BulkUploadResultRecord fromResults(
            List<FileUploadResultRecord> results, 
            Instant startTime, 
            Instant endTime) {
        
        if (results == null || results.isEmpty()) {
            return new BulkUploadResultRecord(
                List.of(), 0, 0, 0, true, startTime, endTime, 0L, 0L, Map.of(), 0, false
            );
        }
        
        int totalProcessed = results.size();
        int totalSuccess = (int) results.stream().filter(FileUploadResultRecord::success).count();
        int totalFailed = totalProcessed - totalSuccess;
        boolean overallSuccess = totalFailed == 0;
        long processingTime = endTime.toEpochMilli() - startTime.toEpochMilli();
        long totalSize = results.stream().mapToLong(FileUploadResultRecord::fileSize).sum();
        
        Map<String, Object> metadata = Map.of(
            "successRate", String.format(java.util.Locale.US, "%.2f%%", (double) totalSuccess / totalProcessed * 100),
            "averageFileSize", totalProcessed > 0 ? totalSize / totalProcessed : 0,
            "processingRateFilesPerSecond", processingTime > 0 ? (double) totalProcessed / (processingTime / 1000.0) : 0
        );
        
        // Count cancelled files
        int totalCancelled = (int) results.stream()
                .filter(result -> !result.success() && 
                        result.errorReason() != null && 
                        result.errorReason() == FileErrorReason.BULK_UPLOAD_CANCELLED)
                .count();
        
        boolean wasFailFastTriggered = totalCancelled > 0;
        
        return new BulkUploadResultRecord(
            results, totalProcessed, totalSuccess, totalFailed, 
            overallSuccess, startTime, endTime, processingTime, totalSize, metadata,
            totalCancelled, wasFailFastTriggered
        );
    }
    
    /**
     * Retorna apenas os resultados bem-sucedidos
     */
    public List<FileUploadResultRecord> getSuccessfulResults() {
        return results.stream()
                .filter(FileUploadResultRecord::success)
                .toList();
    }
    
    /**
     * Retorna apenas os resultados que falharam
     */
    public List<FileUploadResultRecord> getFailedResults() {
        return results.stream()
                .filter(result -> !result.success())
                .toList();
    }
    
    /**
     * Retorna apenas os resultados que foram cancelados devido ao fail-fast mode
     */
    public List<FileUploadResultRecord> getCancelledResults() {
        return results.stream()
                .filter(result -> !result.success() && 
                        result.errorReason() != null && 
                        result.errorReason() == FileErrorReason.BULK_UPLOAD_CANCELLED)
                .toList();
    }
    
    /**
     * Calcula a taxa de sucesso como percentual
     */
    public double getSuccessRate() {
        return totalProcessed > 0 ? (double) totalSuccess / totalProcessed * 100 : 0.0;
    }
    
    /**
     * Builder pattern para criação fluente
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private List<FileUploadResultRecord> results = List.of();
        private int totalProcessed;
        private int totalSuccess;
        private int totalFailed;
        private Boolean overallSuccess;
        private Instant startTimestamp = Instant.now();
        private Instant endTimestamp = Instant.now();
        private long processingTimeMs;
        private long totalSizeBytes;
        private Map<String, Object> metadata = Map.of();
        private int totalCancelled = 0;
        private boolean wasFailFastTriggered = false;
        
        public Builder results(List<FileUploadResultRecord> results) {
            this.results = results;
            return this;
        }
        
        public Builder totalProcessed(int totalProcessed) {
            this.totalProcessed = totalProcessed;
            return this;
        }
        
        public Builder totalSuccess(int totalSuccess) {
            this.totalSuccess = totalSuccess;
            return this;
        }
        
        public Builder totalFailed(int totalFailed) {
            this.totalFailed = totalFailed;
            return this;
        }
        
        public Builder overallSuccess(Boolean overallSuccess) {
            this.overallSuccess = overallSuccess;
            return this;
        }
        
        public Builder startTimestamp(Instant startTimestamp) {
            this.startTimestamp = startTimestamp;
            return this;
        }
        
        public Builder endTimestamp(Instant endTimestamp) {
            this.endTimestamp = endTimestamp;
            return this;
        }
        
        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }
        
        public Builder totalSizeBytes(long totalSizeBytes) {
            this.totalSizeBytes = totalSizeBytes;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder totalCancelled(int totalCancelled) {
            this.totalCancelled = totalCancelled;
            return this;
        }
        
        public Builder wasFailFastTriggered(boolean wasFailFastTriggered) {
            this.wasFailFastTriggered = wasFailFastTriggered;
            return this;
        }
        
        public BulkUploadResultRecord build() {
            return new BulkUploadResultRecord(
                results, totalProcessed, totalSuccess, totalFailed,
                overallSuccess, startTimestamp, endTimestamp,
                processingTimeMs, totalSizeBytes, metadata,
                totalCancelled, wasFailFastTriggered
            );
        }
    }
}
