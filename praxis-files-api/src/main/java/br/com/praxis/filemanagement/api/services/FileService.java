package br.com.praxis.filemanagement.api.services;

import br.com.praxis.filemanagement.api.dtos.BulkUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Interface principal do serviço de gerenciamento de arquivos.
 * 
 * Fornece operações para upload de arquivo único e múltiplo com
 * validações de segurança, controle de vírus e auditoria completa.
 *
 * @author ErgonX
 * @since 1.0.0
 */
public interface FileService {
    
    /**
     * Upload de arquivo único com opções configuráveis
     * 
     * @param file arquivo a ser enviado
     * @param options opções de configuração do upload (pode ser null)
     * @return resultado detalhado do upload
     */
    FileUploadResultRecord uploadFile(MultipartFile file, FileUploadOptionsRecord options);
    
    /**
     * Upload múltiplo de arquivos com processamento paralelo
     * 
     * @param files array de arquivos a serem enviados
     * @param options opções de configuração aplicadas a todos os arquivos (pode ser null)
     * @return resultado consolidado com estatísticas e resultados individuais
     * @since 1.1.0
     */
    BulkUploadResultRecord uploadMultipleFiles(MultipartFile[] files, FileUploadOptionsRecord options);
    
}
