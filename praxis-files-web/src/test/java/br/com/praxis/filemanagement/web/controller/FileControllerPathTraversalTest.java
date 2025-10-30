package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.services.PresignedUrlService;
import br.com.praxis.filemanagement.core.services.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the controller rejects path traversal attempts and maps them to HTTP 400.
 */
class FileControllerPathTraversalTest {

    private final FileService fileService = Mockito.mock(FileService.class);
    private final QuotaService quotaService = Mockito.mock(QuotaService.class);
    private final PresignedUrlService presignedUrlService = Mockito.mock(PresignedUrlService.class);
    private final FileController controller = new FileController(fileService, new ObjectMapper(), quotaService, presignedUrlService);

    @Test
    @DisplayName("Should return 400 when path traversal is detected")
    void shouldRejectPathTraversal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../evil.txt",
            "text/plain",
            "data".getBytes()
        );

        FileUploadResultRecord result = FileUploadResultRecord.error(
            "../evil.txt",
            FileErrorReason.PATH_TRAVERSAL,
            "Path traversal detected"
        );

        Mockito.when(fileService.uploadFile(Mockito.any(), Mockito.isNull())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = controller.uploadSingleFile(file, null, null, null);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}
