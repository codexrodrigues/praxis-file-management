package br.com.praxis.filemanagement.core.utils;

import java.text.Normalizer;
import java.util.Objects;
import java.util.UUID;

/**
 * Utilitário para manipulação consistente de nomes de arquivos.
 * Centraliza toda a lógica de sanitização, extração de extensões e geração de nomes.
 *
 * @since 1.0.0
 * @author Claude Code Refactoring
 */
public final class FileNameHandler {

    private FileNameHandler() {
        // Utility class - não deve ser instanciada
    }

    /**
     * Representa um nome de arquivo decomposto em suas partes.
     *
     * @param baseName Nome do arquivo sem extensão (já sanitizado)
     * @param extension Extensão do arquivo sem o ponto (ex: "pdf", "txt")
     * @param originalName Nome original antes da sanitização
     */
    public record FileNameComponents(
        String baseName,
        String extension,
        String originalName
    ) {
        public FileNameComponents {
            Objects.requireNonNull(baseName, "baseName não pode ser null");
            Objects.requireNonNull(extension, "extension não pode ser null");
            Objects.requireNonNull(originalName, "originalName não pode ser null");
        }

        /**
         * Reconstrói o nome completo do arquivo.
         * @return Nome completo no formato "baseName.extension" ou apenas "baseName" se não houver extensão
         */
        public String getFullName() {
            return extension.isEmpty() ? baseName : baseName + "." + extension;
        }

        /**
         * Verifica se o arquivo possui extensão.
         * @return true se houver extensão, false caso contrário
         */
        public boolean hasExtension() {
            return !extension.isEmpty();
        }
    }

    /**
     * Decompõe e sanitiza um nome de arquivo em suas partes componentes.
     *
     * @param filename Nome do arquivo original
     * @return Componentes do arquivo sanitizados
     * @throws IllegalArgumentException se filename for null ou vazio
     */
    public static FileNameComponents decompose(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Nome do arquivo não pode ser null ou vazio");
        }

        // Normalizar para forma canônica e extrair extensão
        String normalized = Normalizer.normalize(filename, Normalizer.Form.NFKC);
        String baseName = normalized;
        String extension = "";
        int lastDotIndex = normalized.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < normalized.length() - 1) {
            baseName = normalized.substring(0, lastDotIndex);
            extension = normalized.substring(lastDotIndex + 1); // SEM o ponto
        }

        // Sanitizar nome base
        String sanitizedBaseName = sanitizeBaseName(baseName);

        // Sanitizar extensão
        String sanitizedExtension = sanitizeExtension(extension);

        return new FileNameComponents(sanitizedBaseName, sanitizedExtension, normalized);
    }

    /**
     * Sanitiza apenas o nome base (sem extensão).
     *
     * @param baseName Nome base a ser sanitizado
     * @return Nome base sanitizado
     */
    private static String sanitizeBaseName(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            return generateFallbackBaseName();
        }

        // Permitir apenas letras, números, underscores e hífens
        String sanitized = Normalizer.normalize(baseName, Normalizer.Form.NFKC)
                                  .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                                  .replaceAll("_{2,}", "_")      // Múltiplos underscores para um
                                  .replaceAll("^_+", "")         // Remove underscores no início
                                  .replaceAll("_+$", "");        // Remove underscores no fim

        // Se ficou vazio ou é nome reservado, gerar nome fallback
        if (sanitized.isEmpty() || isWindowsReservedName(sanitized)) {
            sanitized = generateFallbackBaseName();
        }

        return sanitized;
    }

    /**
     * Sanitiza extensão de arquivo.
     *
     * @param extension Extensão a ser sanitizada (sem o ponto)
     * @return Extensão sanitizada
     */
    private static String sanitizeExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "";
        }

        // Remover caracteres perigosos da extensão
        String sanitized = extension.replaceAll("[^a-zA-Z0-9]", "");

        // Limitar tamanho da extensão (máximo 10 caracteres)
        if (sanitized.length() > 10) {
            sanitized = sanitized.substring(0, 10);
        }

        return sanitized.toLowerCase(); // Extensões em minúsculo por convenção
    }

    private static boolean isWindowsReservedName(String name) {
        String upper = name.toUpperCase();
        String[] reserved = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
                             "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5",
                             "LPT6", "LPT7", "LPT8", "LPT9"};
        for (String r : reserved) {
            if (r.equals(upper)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gera um nome base fallback quando o nome original fica vazio após sanitização.
     *
     * @return Nome base gerado automaticamente
     */
    private static String generateFallbackBaseName() {
        return "file_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Reconstrói um nome de arquivo sanitizado a partir de seus componentes.
     *
     * @param components Componentes do arquivo
     * @return Nome de arquivo completo sanitizado
     */
    public static String reconstruct(FileNameComponents components) {
        Objects.requireNonNull(components, "Components não pode ser null");
        return components.getFullName();
    }

    /**
     * Sanitiza um nome de arquivo completo de forma consistente.
     * Este é o método principal que deve ser usado em toda a aplicação.
     *
     * @param filename Nome do arquivo original
     * @return Nome de arquivo sanitizado
     * @throws IllegalArgumentException se filename for null ou vazio
     */
    public static String sanitize(String filename) {
        return reconstruct(decompose(filename));
    }

    /**
     * Cria uma versão incrementada de um nome de arquivo.
     * Exemplo: "document.pdf" -> "document(1).pdf"
     *
     * @param components Componentes do arquivo base
     * @param increment Número do incremento
     * @return Nome com incremento aplicado
     * @throws IllegalArgumentException se increment for menor que 1
     */
    public static String createIncrementalName(FileNameComponents components, int increment) {
        Objects.requireNonNull(components, "Components não pode ser null");
        if (increment < 1) {
            throw new IllegalArgumentException("Increment deve ser maior que 0");
        }

        String incrementedBaseName = components.baseName() + "(" + increment + ")";
        return components.hasExtension()
            ? incrementedBaseName + "." + components.extension()
            : incrementedBaseName;
    }

    /**
     * Valida se um nome de arquivo está em formato consistente.
     *
     * @param filename Nome do arquivo a ser validado
     * @return true se o nome está no formato esperado, false caso contrário
     */
    public static boolean isValid(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        try {
            FileNameComponents components = decompose(filename);
            String reconstructed = reconstruct(components);
            return filename.equals(reconstructed);
        } catch (Exception e) {
            return false;
        }
    }
}
