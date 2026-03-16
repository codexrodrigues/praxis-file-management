package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.exception.QuotaExceededException;
import br.com.praxis.filemanagement.core.services.PresignedUrlService;
import br.com.praxis.filemanagement.core.services.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileControllerQuotaTest {

    private final FileService fileService = Mockito.mock(FileService.class);
    private final QuotaService quotaService = Mockito.mock(QuotaService.class);
    private final PresignedUrlService presignedUrlService = Mockito.mock(PresignedUrlService.class);
    private final FileController controller = new FileController(fileService, new ObjectMapper(), quotaService, presignedUrlService);

    @Test
    @DisplayName("Should reject upload when tenant quota is exceeded")
    void shouldRejectUploadWhenTenantQuotaExceeded() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        Mockito.when(quotaService.isTenantQuotaExceeded("test-tenant")).thenReturn(true);

        assertThrows(
            QuotaExceededException.class,
            () -> controller.uploadSingleFile(file, null, null, null, "test-tenant", null)
        );
    }

    @Test
    @DisplayName("Should record quota usage after successful upload")
    void shouldRecordQuotaUsageAfterSuccessfulUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        FileUploadResultRecord result = FileUploadResultRecord.success("a.txt", "a.txt", "id-1", 5L, "text/plain");

        Mockito.when(fileService.uploadFile(Mockito.any(), Mockito.any())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
            controller.uploadSingleFile(file, null, null, null, "tenant-a", "user-a");

        assertEquals(201, response.getStatusCode().value());
        Mockito.verify(quotaService).recordTenantUpload("tenant-a");
        Mockito.verify(quotaService).recordUserUpload("user-a");
    }
}
