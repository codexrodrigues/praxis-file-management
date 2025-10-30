package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.BulkUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.services.QuotaService;
import br.com.praxis.filemanagement.core.services.PresignedUrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FileControllerBulkUploadTest {

    private final FileService fileService = Mockito.mock(FileService.class);
    private final QuotaService quotaService = Mockito.mock(QuotaService.class);
    private final PresignedUrlService presignedUrlService = Mockito.mock(PresignedUrlService.class);
    private final FileController controller = new FileController(fileService, new ObjectMapper(), quotaService, presignedUrlService);

    @Test
    @DisplayName("Should return 207 when some files fail and others succeed")
    void shouldReturnMultiStatusOnPartialFailures() throws Exception {
        MultipartFile ok = new MockMultipartFile("files", "ok.txt", "text/plain", "ok".getBytes());
        MultipartFile bad = new MockMultipartFile("files", "bad.txt", "text/plain", "bad".getBytes());

        FileUploadResultRecord success = FileUploadResultRecord.success("ok.txt", "ok.txt", "id1", 2L, "text/plain");
        FileUploadResultRecord error = FileUploadResultRecord.error("bad.txt", FileErrorReason.FILE_TOO_LARGE, "Too big");

        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(success, error));
        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), Mockito.isNull())).thenReturn(bulk);

        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(new MultipartFile[]{ok, bad}, null, null, null, null, null, null);

        assertEquals(HttpStatus.MULTI_STATUS, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
