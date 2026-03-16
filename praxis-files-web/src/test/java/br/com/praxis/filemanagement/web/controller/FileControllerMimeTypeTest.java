package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import br.com.praxis.filemanagement.api.services.FileService;
import br.com.praxis.filemanagement.core.services.PresignedUrlService;
import br.com.praxis.filemanagement.core.services.QuotaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FileControllerMimeTypeTest {

    private final FileService fileService = Mockito.mock(FileService.class);
    private final QuotaService quotaService = Mockito.mock(QuotaService.class);
    private final PresignedUrlService presignedUrlService = Mockito.mock(PresignedUrlService.class);
    private final FileController controller = new FileController(fileService, new ObjectMapper(), quotaService, presignedUrlService);

    @Test
    @DisplayName("Should return 415 when MIME type mismatch occurs")
    void shouldReturnUnsupportedMediaType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.png",
            "image/png",
            "data".getBytes()
        );

        FileUploadResultRecord result = FileUploadResultRecord.error(
            "test.png",
            FileErrorReason.MIME_TYPE_MISMATCH,
            "Mismatch",
            file.getSize()
        );

        Mockito.when(fileService.uploadFile(Mockito.any(), Mockito.isNull())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = controller.uploadSingleFile(file, null, null, null, null, null);

        assertEquals(415, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("failure", response.getBody().get("status"));
        assertEquals("Tipo MIME não corresponde ao arquivo", response.getBody().get("message"));
        assertFalse(response.getBody().containsKey("code"));
        assertFalse(response.getBody().containsKey("details"));
        assertTrue(response.getBody().containsKey("errors"));
    }

    @Test
    @DisplayName("Should merge metadata and conflictPolicy params into upload options")
    void shouldMergeMetadataAndConflictPolicyIntoUploadOptions() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.png",
            "image/png",
            "data".getBytes()
        );

        FileUploadResultRecord result = FileUploadResultRecord.success(
            "test.png",
            "stored-test.png",
            "id-123",
            file.getSize(),
            "image/png"
        );

        ArgumentCaptor<FileUploadOptionsRecord> optionsCaptor = ArgumentCaptor.forClass(FileUploadOptionsRecord.class);
        Mockito.when(fileService.uploadFile(Mockito.any(), optionsCaptor.capture())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response = controller.uploadSingleFile(
            file,
            null,
            "{\"category\":\"invoice\",\"priority\":10}",
            "overwrite",
            null,
            null
        );

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("success", response.getBody().get("status"));
        assertEquals("Upload realizado com sucesso", response.getBody().get("message"));
        assertFalse(response.getBody().containsKey("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data);
        assertEquals("test.png", data.get("originalFilename"));
        assertEquals("stored-test.png", data.get("serverFilename"));
        assertEquals("id-123", data.get("fileId"));
        FileUploadOptionsRecord captured = optionsCaptor.getValue();
        assertNotNull(captured);
        assertEquals(NameConflictPolicy.OVERWRITE, captured.nameConflictPolicy());
        Map<String, String> expectedMetadata = new HashMap<>();
        expectedMetadata.put("category", "invoice");
        expectedMetadata.put("priority", "10");
        assertEquals(expectedMetadata, captured.customMetadata());
    }

    @Test
    @DisplayName("Should reject malformed single upload options JSON")
    void shouldRejectMalformedSingleUploadOptionsJson() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "data".getBytes()
        );

        assertThrows(
            com.fasterxml.jackson.core.JsonProcessingException.class,
            () -> controller.uploadSingleFile(file, "{", null, null, null, null)
        );
    }

    @Test
    @DisplayName("Should reject invalid conflict policy on single upload")
    void shouldRejectInvalidConflictPolicyOnSingleUpload() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "data".getBytes()
        );

        assertThrows(
            com.fasterxml.jackson.core.JsonProcessingException.class,
            () -> controller.uploadSingleFile(file, null, null, "bad-policy", null, null)
        );
    }
}
