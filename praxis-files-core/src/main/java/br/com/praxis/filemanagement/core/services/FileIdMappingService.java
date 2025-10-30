package br.com.praxis.filemanagement.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Serviço responsável por gerenciar o mapeamento entre fileId e arquivos reais.
 * Resolve o problema de arquitetura onde fileId não tinha correlação com o arquivo.
 * 
 * @since 1.0.0
 * @author Claude Code Refactoring
 */
@Service
public class FileIdMappingService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileIdMappingService.class);
    
    // Cache em memória para mapeamentos (em produção seria cache distribuído ou banco)
    private final ConcurrentMap<String, FileMapping> fileMappings = new ConcurrentHashMap<>();
    
    /**
     * Representa o mapeamento entre fileId e arquivo físico.
     * 
     * @param fileId ID único do arquivo
     * @param serverFilename Nome do arquivo no servidor
     * @param originalFilename Nome original do arquivo
     * @param uploadPath Caminho do diretório de upload
     * @param creationTime Timestamp de criação
     * @param fileSize Tamanho do arquivo em bytes
     * @param mimeType MIME type do arquivo
     */
    public record FileMapping(
        String fileId,
        String serverFilename,
        String originalFilename,
        String uploadPath,
        LocalDateTime creationTime,
        long fileSize,
        String mimeType
    ) {
        public FileMapping {
            Objects.requireNonNull(fileId, "fileId não pode ser null");
            Objects.requireNonNull(serverFilename, "serverFilename não pode ser null");
            Objects.requireNonNull(originalFilename, "originalFilename não pode ser null");
            Objects.requireNonNull(uploadPath, "uploadPath não pode ser null");
            Objects.requireNonNull(creationTime, "creationTime não pode ser null");
        }
        
        /**
         * Retorna o caminho completo do arquivo.
         */
        public Path getFullPath() {
            return Path.of(uploadPath, serverFilename);
        }
        
        /**
         * Verifica se o mapeamento é válido (arquivo existe fisicamente).
         */
        public boolean isValid() {
            return getFullPath().toFile().exists();
        }
    }
    
    /**
     * Estratégias de geração de fileId.
     */
    public enum FileIdStrategy {
        SECURE_HASH,    // Hash baseado em conteúdo + timestamp
        UUID_BASED,     // UUID com prefixo significativo
        HYBRID          // Combinação de hash + UUID
    }
    
    /**
     * Gera um fileId único e correlacionado com o arquivo.
     * 
     * @param serverFilename Nome do arquivo no servidor
     * @param originalFilename Nome original do arquivo
     * @param uploadPath Caminho do diretório de upload
     * @param fileSize Tamanho do arquivo
     * @param mimeType MIME type do arquivo
     * @param strategy Estratégia de geração
     * @return fileId único gerado
     */
    public String generateFileId(String serverFilename, String originalFilename, 
                               String uploadPath, long fileSize, String mimeType,
                               FileIdStrategy strategy) {
        
        validateParameters(serverFilename, originalFilename, uploadPath);
        
        String fileId = switch (strategy) {
            case SECURE_HASH -> generateSecureHashId(serverFilename, originalFilename, fileSize);
            case UUID_BASED -> generateUuidBasedId(originalFilename);
            case HYBRID -> generateHybridId(serverFilename, originalFilename, fileSize);
        };
        
        // Criar e armazenar mapeamento
        FileMapping mapping = new FileMapping(
            fileId, serverFilename, originalFilename, uploadPath,
            LocalDateTime.now(), fileSize, mimeType
        );
        
        fileMappings.put(fileId, mapping);
        
        logger.debug("Generated fileId: {} for file: {} using strategy: {}", 
                   fileId, serverFilename, strategy);
        
        return fileId;
    }
    
    /**
     * Gera fileId usando estratégia padrão (HYBRID).
     */
    public String generateFileId(String serverFilename, String originalFilename, 
                               String uploadPath, long fileSize, String mimeType) {
        return generateFileId(serverFilename, originalFilename, uploadPath, 
                            fileSize, mimeType, FileIdStrategy.HYBRID);
    }
    
    /**
     * Recupera o mapeamento de um arquivo pelo fileId.
     * 
     * @param fileId ID do arquivo
     * @return FileMapping se encontrado, null caso contrário
     */
    public FileMapping getFileMapping(String fileId) {
        Objects.requireNonNull(fileId, "fileId não pode ser null");
        
        FileMapping mapping = fileMappings.get(fileId);
        
        if (mapping != null && !mapping.isValid()) {
            // Arquivo foi removido fisicamente - limpar mapeamento
            logger.warn("File mapping {} points to non-existent file: {}", 
                       fileId, mapping.getFullPath());
            fileMappings.remove(fileId);
            return null;
        }
        
        return mapping;
    }
    
    /**
     * Verifica se um fileId existe e é válido.
     * 
     * @param fileId ID do arquivo
     * @return true se existir e for válido, false caso contrário
     */
    public boolean isValidFileId(String fileId) {
        return getFileMapping(fileId) != null;
    }
    
    /**
     * Remove um mapeamento (quando arquivo é deletado).
     * 
     * @param fileId ID do arquivo
     * @return true se removido, false se não existia
     */
    public boolean removeMapping(String fileId) {
        Objects.requireNonNull(fileId, "fileId não pode ser null");
        
        FileMapping removed = fileMappings.remove(fileId);
        if (removed != null) {
            logger.debug("Removed file mapping: {} -> {}", fileId, removed.serverFilename());
            return true;
        }
        
        return false;
    }
    
    /**
     * Retorna o caminho completo do arquivo pelo fileId.
     * 
     * @param fileId ID do arquivo
     * @return Path do arquivo ou null se não encontrado
     */
    public Path resolveFilePath(String fileId) {
        FileMapping mapping = getFileMapping(fileId);
        return mapping != null ? mapping.getFullPath() : null;
    }
    
    /**
     * Gera fileId usando hash seguro.
     */
    private String generateSecureHashId(String serverFilename, String originalFilename, long fileSize) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Combinar informações para gerar hash único
            String data = originalFilename + "|" + serverFilename + "|" + fileSize + "|" + 
                         System.currentTimeMillis();
            
            byte[] hash = digest.digest(data.getBytes());
            String hashStr = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            
            // Usar apenas primeiros 16 caracteres para fileId mais curto
            return "hash_" + hashStr.substring(0, 16);
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 not available, falling back to UUID", e);
            return "uuid_" + UUID.randomUUID().toString();
        }
    }
    
    /**
     * Gera fileId baseado em UUID com prefixo significativo.
     */
    private String generateUuidBasedId(String originalFilename) {
        String prefix = extractFilePrefix(originalFilename);
        return prefix + "_" + UUID.randomUUID().toString();
    }
    
    /**
     * Gera fileId híbrido (hash + UUID parcial).
     */
    private String generateHybridId(String serverFilename, String originalFilename, long fileSize) {
        // Usar hash dos primeiros caracteres + UUID curto
        String prefix = extractFilePrefix(originalFilename);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuidShort = UUID.randomUUID().toString().substring(0, 8);
        
        return String.format("%s_%s_%s", prefix, timestamp, uuidShort);
    }
    
    /**
     * Extrai prefixo significativo do nome do arquivo.
     */
    private String extractFilePrefix(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "file";
        }
        
        // Remover extensão e pegar primeiros caracteres
        String baseName = filename.contains(".") 
            ? filename.substring(0, filename.lastIndexOf('.'))
            : filename;
            
        // Sanitizar e truncar
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9]", "")
                                  .toLowerCase();
        
        if (sanitized.isEmpty()) {
            return "file";
        }
        
        // Máximo 8 caracteres para o prefixo
        return sanitized.length() > 8 ? sanitized.substring(0, 8) : sanitized;
    }
    
    /**
     * Valida parâmetros de entrada.
     */
    private void validateParameters(String serverFilename, String originalFilename, String uploadPath) {
        Objects.requireNonNull(serverFilename, "serverFilename não pode ser null");
        Objects.requireNonNull(originalFilename, "originalFilename não pode ser null");
        Objects.requireNonNull(uploadPath, "uploadPath não pode ser null");
        
        if (serverFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("serverFilename não pode ser vazio");
        }
        if (originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("originalFilename não pode ser vazio");
        }
        if (uploadPath.trim().isEmpty()) {
            throw new IllegalArgumentException("uploadPath não pode ser vazio");
        }
    }
    
    /**
     * Retorna estatísticas do serviço para monitoramento.
     */
    public MappingStatistics getStatistics() {
        int totalMappings = fileMappings.size();
        long validMappings = fileMappings.values().stream()
                                       .mapToLong(mapping -> mapping.isValid() ? 1 : 0)
                                       .sum();
        
        return new MappingStatistics(totalMappings, (int) validMappings);
    }
    
    /**
     * Estatísticas do serviço de mapeamento.
     */
    public record MappingStatistics(int totalMappings, int validMappings) {
        public int getInvalidMappings() {
            return totalMappings - validMappings;
        }
        
        public double getValidPercentage() {
            return totalMappings > 0 ? (double) validMappings / totalMappings * 100 : 0;
        }
    }
}