package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.utils.FileNameHandler;
import br.com.praxis.filemanagement.core.utils.FileNameHandler.FileNameComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Serviço thread-safe para geração de nomes únicos de arquivos.
 * Resolve o problema de race condition na política RENAME.
 * 
 * @since 1.0.0
 * @author Claude Code Refactoring
 */
@Service
public class ThreadSafeFileNamingService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreadSafeFileNamingService.class);
    
    private static final int MAX_RENAME_ATTEMPTS = 1000;
    private static final int RANDOM_SUFFIX_LENGTH = 4;
    private final ConcurrentHashMap<Path, Set<String>> reservedNamesByDirectory = new ConcurrentHashMap<>();
    
    /**
     * Resultado da geração de nome único.
     * 
     * @param finalName Nome final gerado
     * @param components Componentes do nome final
     * @param attemptsUsed Número de tentativas utilizadas
     * @param strategy Estratégia que foi bem-sucedida
     */
    public record UniqueNameResult(
        String finalName,
        FileNameComponents components,
        int attemptsUsed,
        NamingStrategy strategy
    ) {}
    
    /**
     * Estratégias de nomenclatura utilizadas.
     */
    public enum NamingStrategy {
        ORIGINAL,     // Nome original estava disponível
        INCREMENTAL,  // Usado incremento numérico
        RANDOM_UUID,  // Fallback para UUID
        HYBRID        // Combinação de incremento com sufixo aleatório
    }
    
    /**
     * Gera um nome único de arquivo usando a política RENAME de forma thread-safe.
     * 
     * @param originalFilename Nome do arquivo original
     * @param targetDirectory Diretório onde o arquivo será criado
     * @return Resultado com o nome único gerado
     * @throws IOException se houver erro de I/O
     * @throws IllegalArgumentException se parâmetros forem inválidos
     */
    public UniqueNameResult generateUniqueNameForRename(String originalFilename, Path targetDirectory) 
            throws IOException {
        
        validateParameters(originalFilename, targetDirectory);
        Path normalizedDirectory = targetDirectory.toAbsolutePath().normalize();
        
        FileNameComponents components = FileNameHandler.decompose(originalFilename);
        
        // Estratégia 1: Tentar nome original primeiro
        UniqueNameResult originalResult = tryOriginalName(components, normalizedDirectory);
        if (originalResult != null) {
            return originalResult;
        }
        
        // Estratégia 2: Tentar incrementos numéricos
        UniqueNameResult incrementalResult = tryIncrementalNaming(components, normalizedDirectory);
        if (incrementalResult != null) {
            return incrementalResult;
        }
        
        // Estratégia 3: Hybrid approach com sufixo aleatório
        UniqueNameResult hybridResult = tryHybridNaming(components, normalizedDirectory);
        if (hybridResult != null) {
            return hybridResult;
        }
        
        // Estratégia 4: Fallback para UUID (sempre funciona)
        return generateUuidFallback(components);
    }
    
    /**
     * Valida parâmetros de entrada.
     */
    private void validateParameters(String originalFilename, Path targetDirectory) {
        Objects.requireNonNull(originalFilename, "Original filename não pode ser null");
        Objects.requireNonNull(targetDirectory, "Target directory não pode ser null");
        
        if (originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Original filename não pode ser vazio");
        }
        
        if (!Files.exists(targetDirectory)) {
            throw new IllegalArgumentException("Target directory deve existir: " + targetDirectory);
        }
        
        if (!Files.isDirectory(targetDirectory)) {
            throw new IllegalArgumentException("Target path deve ser um diretório: " + targetDirectory);
        }
    }
    
    /**
     * Tenta usar o nome original.
     */
    private UniqueNameResult tryOriginalName(FileNameComponents components, Path targetDirectory) 
            throws IOException {
        
        String originalName = components.getFullName();
        Path targetPath = targetDirectory.resolve(originalName);
        
        if (reserveNameIfAvailable(targetPath)) {
            logger.debug("Nome original disponível: {}", originalName);
            return new UniqueNameResult(originalName, components, 1, NamingStrategy.ORIGINAL);
        }
        
        return null;
    }
    
    /**
     * Tenta nomenclatura incremental thread-safe.
     */
    private UniqueNameResult tryIncrementalNaming(FileNameComponents components, Path targetDirectory) 
            throws IOException {
        
        for (int i = 1; i <= MAX_RENAME_ATTEMPTS; i++) {
            String incrementalName = FileNameHandler.createIncrementalName(components, i);
            Path targetPath = targetDirectory.resolve(incrementalName);
            
            if (reserveNameIfAvailable(targetPath)) {
                logger.debug("Nome incremental disponível após {} tentativas: {}", i, incrementalName);
                return new UniqueNameResult(incrementalName, components, i + 1, NamingStrategy.INCREMENTAL);
            }
            
            // Log a cada 100 tentativas para debug
            if (i % 100 == 0) {
                logger.debug("Tentativa incremental {}/{}: {}", i, MAX_RENAME_ATTEMPTS, incrementalName);
            }
        }
        
        logger.warn("Máximo de tentativas incrementais atingido: {}", MAX_RENAME_ATTEMPTS);
        return null;
    }
    
    /**
     * Tenta abordagem híbrida com sufixo aleatório.
     */
    private UniqueNameResult tryHybridNaming(FileNameComponents components, Path targetDirectory) 
            throws IOException {
        
        // Tentar algumas combinações com sufixo aleatório
        for (int attempt = 1; attempt <= 10; attempt++) {
            String randomSuffix = generateRandomSuffix();
            String hybridBaseName = components.baseName() + "_" + randomSuffix;
            
            FileNameComponents hybridComponents = new FileNameComponents(
                hybridBaseName, 
                components.extension(), 
                components.originalName()
            );
            
            String hybridName = hybridComponents.getFullName();
            Path targetPath = targetDirectory.resolve(hybridName);
            
            if (reserveNameIfAvailable(targetPath)) {
                logger.debug("Nome híbrido disponível após {} tentativas: {}", attempt, hybridName);
                return new UniqueNameResult(hybridName, hybridComponents, attempt, NamingStrategy.HYBRID);
            }
        }
        
        logger.warn("Abordagem híbrida falhou após 10 tentativas");
        return null;
    }
    
    /**
     * Gera fallback com UUID (sempre funciona).
     */
    private UniqueNameResult generateUuidFallback(FileNameComponents components) {
        String uuidName = UUID.randomUUID().toString();
        String fallbackName = components.hasExtension() 
            ? uuidName + "." + components.extension()
            : uuidName;
            
        FileNameComponents fallbackComponents = new FileNameComponents(
            uuidName,
            components.extension(),
            components.originalName()
        );
        
        logger.info("Usando fallback UUID para arquivo: {} -> {}", 
                   components.originalName(), fallbackName);
        
        return new UniqueNameResult(fallbackName, fallbackComponents, 1, NamingStrategy.RANDOM_UUID);
    }
    
    /**
     * Verifica se um nome está disponível de forma thread-safe.
     * Usa tentativa de criação atômica para evitar race conditions.
     */
    private boolean reserveNameIfAvailable(Path targetPath) throws IOException {
        Path normalizedPath = targetPath.toAbsolutePath().normalize();
        Path directory = normalizedPath.getParent();
        String filename = normalizedPath.getFileName().toString();
        Set<String> reservedNames = reservedNamesByDirectory.computeIfAbsent(
            directory,
            ignored -> ConcurrentHashMap.newKeySet()
        );

        synchronized (reservedNames) {
            if (reservedNames.contains(filename)) {
                return false;
            }

            if (!isNameAvailable(normalizedPath)) {
                return false;
            }

            reservedNames.add(filename);
            return true;
        }
    }

    private boolean isNameAvailable(Path targetPath) throws IOException {
        try {
            // Tentativa atômica de criação do arquivo
            // Se o arquivo já existe, createFile() lança FileAlreadyExistsException
            Files.createFile(targetPath);
            
            // Se chegou aqui, arquivo foi criado com sucesso
            // Deletar imediatamente pois só estamos testando disponibilidade
            Files.deleteIfExists(targetPath);
            
            return true;
            
        } catch (java.nio.file.FileAlreadyExistsException e) {
            // Arquivo já existe - nome não disponível
            return false;
        } catch (IOException e) {
            // Outros erros de I/O (permissões, etc.)
            logger.warn("Erro ao verificar disponibilidade do nome {}: {}", targetPath, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Gera sufixo aleatório para nomenclatura híbrida.
     */
    private String generateRandomSuffix() {
        StringBuilder suffix = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (int i = 0; i < RANDOM_SUFFIX_LENGTH; i++) {
            // Usar apenas caracteres alfanuméricos (sem ambiguidade)
            char[] chars = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
            suffix.append(chars[random.nextInt(chars.length)]);
        }
        
        return suffix.toString();
    }
    
    /**
     * Gera nome único usando política MAKE_UNIQUE (UUID).
     * 
     * @param originalFilename Nome original do arquivo
     * @return Resultado com nome UUID único
     */
    public UniqueNameResult generateUniqueNameForMakeUnique(String originalFilename) {
        Objects.requireNonNull(originalFilename, "Original filename não pode ser null");
        
        FileNameComponents components = FileNameHandler.decompose(originalFilename);
        return generateUuidFallback(components);
    }

    /**
     * Libera uma reserva de nome anteriormente gerada pela política RENAME.
     */
    public void releaseReservedName(Path targetDirectory, String filename) {
        if (targetDirectory == null || filename == null || filename.isBlank()) {
            return;
        }

        Path normalizedDirectory = targetDirectory.toAbsolutePath().normalize();
        Set<String> reservedNames = reservedNamesByDirectory.get(normalizedDirectory);
        if (reservedNames == null) {
            return;
        }

        reservedNames.remove(filename);
        if (reservedNames.isEmpty()) {
            reservedNamesByDirectory.remove(normalizedDirectory, reservedNames);
        }
    }
}
