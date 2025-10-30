package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Serviço responsável por operações atômicas de arquivo.
 * Garante que operações são reversíveis em caso de falha.
 * 
 * @since 1.0.0
 * @author Claude Code Refactoring
 */
@Service
public class AtomicFileOperationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AtomicFileOperationService.class);
    
    @Autowired
    private FileIdMappingService fileIdMappingService;
    
    /**
     * Contexto de uma operação atômica de upload.
     * Mantém todas as operações realizadas para permitir rollback.
     */
    public static class UploadTransaction {
        private final String transactionId;
        private final List<TransactionStep> steps;
        private boolean committed;
        private boolean rolledBack;
        private final LocalDateTime startTime;
        
        public UploadTransaction() {
            this.transactionId = "tx_" + UUID.randomUUID().toString().substring(0, 8);
            this.steps = new ArrayList<>();
            this.committed = false;
            this.rolledBack = false;
            this.startTime = LocalDateTime.now();
        }
        
        public String getTransactionId() {
            return transactionId;
        }
        
        public boolean isCommitted() {
            return committed;
        }
        
        public boolean isRolledBack() {
            return rolledBack;
        }
        
        public boolean isActive() {
            return !committed && !rolledBack;
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
        
        void addStep(TransactionStep step) {
            if (!isActive()) {
                throw new IllegalStateException("Transaction " + transactionId + " is not active");
            }
            steps.add(step);
        }
        
        void commit() {
            if (!isActive()) {
                throw new IllegalStateException("Transaction " + transactionId + " is not active");
            }
            this.committed = true;
        }
        
        void rollback() {
            if (rolledBack) {
                return; // Already rolled back
            }
            
            // Execute rollback steps in reverse order
            for (int i = steps.size() - 1; i >= 0; i--) {
                try {
                    steps.get(i).rollback();
                } catch (Exception e) {
                    logger.error("Failed to rollback step {} in transaction {}: {}", 
                               i, transactionId, e.getMessage(), e);
                }
            }
            
            this.rolledBack = true;
        }
        
        public int getStepCount() {
            return steps.size();
        }
    }
    
    /**
     * Interface para passos transacionais reversíveis.
     */
    public interface TransactionStep {
        void rollback() throws Exception;
        String getDescription();
    }
    
    /**
     * Passo de criação de arquivo temporário.
     */
    private static class TempFileStep implements TransactionStep {
        private final Path tempFilePath;
        
        public TempFileStep(Path tempFilePath) {
            this.tempFilePath = tempFilePath;
        }
        
        @Override
        public void rollback() throws Exception {
            if (Files.exists(tempFilePath)) {
                Files.delete(tempFilePath);
                logger.debug("Rolled back temp file creation: {}", tempFilePath);
            }
        }
        
        @Override
        public String getDescription() {
            return "Temp file creation: " + tempFilePath;
        }
    }
    
    /**
     * Passo de criação de arquivo físico.
     */
    private static class FileCreationStep implements TransactionStep {
        private final Path filePath;
        
        public FileCreationStep(Path filePath) {
            this.filePath = filePath;
        }
        
        @Override
        public void rollback() throws Exception {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.debug("Rolled back file creation: {}", filePath);
            }
        }
        
        @Override
        public String getDescription() {
            return "File creation: " + filePath;
        }
    }
    
    /**
     * Passo de criação de mapeamento fileId.
     */
    private static class FileMappingStep implements TransactionStep {
        private final String fileId;
        private final FileIdMappingService mappingService;
        
        public FileMappingStep(String fileId, FileIdMappingService mappingService) {
            this.fileId = fileId;
            this.mappingService = mappingService;
        }
        
        @Override
        public void rollback() throws Exception {
            mappingService.removeMapping(fileId);
            logger.debug("Rolled back file mapping: {}", fileId);
        }
        
        @Override
        public String getDescription() {
            return "File mapping: " + fileId;
        }
    }
    
    
    /**
     * Executa upload de arquivo de forma atômica usando Records.
     * 
     * @param file Arquivo a ser enviado
     * @param destinationPath Caminho de destino
     * @param serverFilename Nome do arquivo no servidor
     * @param detectedMimeType MIME type detectado
     * @param uploadPath Diretório de upload
     * @return Transação criada
     * @throws IOException se houver erro de I/O
     */
    public UploadTransaction executeAtomicUpload(
            MultipartFile file,
            Path destinationPath,
            String serverFilename,
            String detectedMimeType,
            Path uploadPath,
            Path tempDir) throws IOException {
        
        Objects.requireNonNull(file, "file não pode ser null");
        Objects.requireNonNull(destinationPath, "destinationPath não pode ser null");
        Objects.requireNonNull(serverFilename, "serverFilename não pode ser null");
        Objects.requireNonNull(uploadPath, "uploadPath não pode ser null");
        Objects.requireNonNull(tempDir, "tempDir não pode ser null");
        
        UploadTransaction transaction = new UploadTransaction();
        
        try {
            // 1. Criar arquivo temporário primeiro
            Path tempFile = Files.createTempFile(tempDir, "upload_", ".tmp");
            transaction.addStep(new TempFileStep(tempFile));

            // 2. Escrever conteúdo do arquivo usando streaming
            try (InputStream in = file.getInputStream();
                 OutputStream out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            logger.debug("File content written to temp file: {}", tempFile);
            
            // 3. Mover arquivo para destino final com fallback cross-device
            moveFile(tempFile, destinationPath);
            transaction.addStep(new FileCreationStep(destinationPath));
            logger.debug("File moved to final destination: {}", destinationPath);
            
            // 4. Criar mapeamento fileId
            String fileId = fileIdMappingService.generateFileId(
                serverFilename, 
                file.getOriginalFilename(), 
                uploadPath.toString(), 
                file.getSize(), 
                detectedMimeType
            );
            transaction.addStep(new FileMappingStep(fileId, fileIdMappingService));
            logger.debug("File ID mapping created: {}", fileId);
            
            // Commit da transação
            transaction.commit();
            logger.debug("Transaction {} committed successfully", transaction.getTransactionId());
            
            return transaction;
            
        } catch (Exception e) {
            logger.error("Transaction {} failed, rolling back: {}", 
                       transaction.getTransactionId(), e.getMessage(), e);
            
            // Rollback automático em caso de erro
            transaction.rollback();
            
            // Re-throw a exceção
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException("Atomic upload failed", e);
            }
        }
    }

    /**
     * Realiza a movimentação do arquivo tentando primeiro um move atômico e
     * fazendo fallback para cópia + deleção em caso de limite de filesystem.
     */
    void moveFile(Path source, Path target) throws IOException {
        try {
            doAtomicMove(source, target);
        } catch (IOException ex) {
            logger.warn("Atomic move failed: {}", ex.getMessage());
            try {
                doStandardMove(source, target);
            } catch (IOException ex2) {
                logger.warn("Standard move failed, falling back to copy: {}", ex2.getMessage());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(source);
            }
        }
    }

    /**
     * Encapsula o move atômico para permitir testes.
     */
    protected void doAtomicMove(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Encapsula o move padrão para permitir testes.
     */
    protected void doStandardMove(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Executa operação com retry automático em caso de falha transitória.
     *
     * @param operation Operação a ser executada
     * @param maxRetries Número máximo de tentativas
     * @param retryDelayMs Delay entre tentativas em ms
     * @return Resultado da operação
     * @throws IOException se todas as tentativas falharem
     */
    public <T> T executeWithRetry(
            AtomicOperation<T> operation,
            int maxRetries,
            long retryDelayMs) throws IOException {
        
        Objects.requireNonNull(operation, "operation não pode ser null");
        
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries deve ser >= 0");
        }
        
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.execute();
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxRetries && isRetriableException(e)) {
                    logger.warn("Attempt {} failed, retrying in {}ms: {}", 
                              attempt + 1, retryDelayMs, e.getMessage());
                    
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Operation interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }
        
        // Todas as tentativas falharam
        if (lastException instanceof IOException) {
            throw (IOException) lastException;
        } else {
            throw new IOException("Operation failed after " + (maxRetries + 1) + " attempts", lastException);
        }
    }
    
    /**
     * Verifica se uma exceção é retriável.
     */
    private boolean isRetriableException(Exception e) {
        // Exceções de I/O temporárias que podem ser retriáveis
        return e instanceof IOException &&
               (e.getMessage().contains("temporarily unavailable") ||
                e.getMessage().contains("resource busy") ||
                e.getMessage().contains("lock"));
    }
    
    /**
     * Interface para operações atômicas.
     */
    @FunctionalInterface
    public interface AtomicOperation<T> {
        T execute() throws Exception;
    }
    
    /**
     * Cria um checkpoint para rollback manual posterior.
     */
    public TransactionCheckpoint createCheckpoint(String description) {
        return new TransactionCheckpoint(UUID.randomUUID().toString(), description);
    }
    
    /**
     * Checkpoint para rollback manual.
     */
    public static class TransactionCheckpoint {
        private final String checkpointId;
        private final String description;
        private final LocalDateTime createdAt;
        private final List<TransactionStep> rollbackSteps;
        
        public TransactionCheckpoint(String checkpointId, String description) {
            this.checkpointId = checkpointId;
            this.description = description;
            this.createdAt = LocalDateTime.now();
            this.rollbackSteps = new ArrayList<>();
        }
        
        public void addRollbackStep(TransactionStep step) {
            rollbackSteps.add(step);
        }
        
        public void rollback() {
            logger.info("Rolling back checkpoint {}: {}", checkpointId, description);
            
            for (int i = rollbackSteps.size() - 1; i >= 0; i--) {
                try {
                    rollbackSteps.get(i).rollback();
                } catch (Exception e) {
                    logger.error("Failed to rollback step in checkpoint {}: {}", 
                               checkpointId, e.getMessage(), e);
                }
            }
        }
        
        public String getCheckpointId() {
            return checkpointId;
        }
        
        public String getDescription() {
            return description;
        }
        
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}
