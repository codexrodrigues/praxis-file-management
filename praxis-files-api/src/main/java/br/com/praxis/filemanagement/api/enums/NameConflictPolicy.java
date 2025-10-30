package br.com.praxis.filemanagement.api.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Política de resolução de conflitos de nomes de arquivo.
 */
@Schema(description = "Política de resolução de conflitos quando o nome do arquivo já existe")
public enum NameConflictPolicy {
    
    @Schema(description = "Gerar erro quando o arquivo já existir")
    ERROR,
    
    @Schema(description = "Pular o upload se o arquivo já existir")
    SKIP,
    
    @Schema(description = "Sobrescrever o arquivo existente")
    OVERWRITE,
    
    @Schema(description = "Gerar nome único automaticamente")
    MAKE_UNIQUE,
    
    @Schema(description = "Renomear adicionando sufixo numérico")
    RENAME
}
