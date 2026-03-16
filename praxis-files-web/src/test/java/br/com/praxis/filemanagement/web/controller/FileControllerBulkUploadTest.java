package br.com.praxis.filemanagement.web.controller;

import br.com.praxis.filemanagement.api.dtos.BulkUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(new MultipartFile[]{ok, bad}, null, null, null, null, null, null, null, null);

        assertEquals(HttpStatus.MULTI_STATUS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("partial_success", response.getBody().get("status"));
        assertEquals("Upload em lote processado com sucesso parcial", response.getBody().get("message"));
    }

    @Test
    @DisplayName("Should expose partial success semantics in compatibility bulk endpoint")
    void shouldExposePartialSuccessSemanticsInCompatibilityEndpoint() throws Exception {
        MultipartFile ok = new MockMultipartFile("files", "ok.txt", "text/plain", "ok".getBytes());
        MultipartFile bad = new MockMultipartFile("files", "bad.txt", "text/plain", "bad".getBytes());
        FileUploadResultRecord success = FileUploadResultRecord.success("ok.txt", "ok.txt", "id1", 2L, "text/plain");
        FileUploadResultRecord error = FileUploadResultRecord.error("bad.txt", FileErrorReason.FILE_TOO_LARGE, "Too big");
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(success, error));

        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), Mockito.isNull())).thenReturn(bulk);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(multipart("/api/files/bulk").file((MockMultipartFile) ok).file((MockMultipartFile) bad))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.status").value("partial_success"))
                .andExpect(jsonPath("$.message").value("Upload em lote processado com sucesso parcial"))
                .andExpect(jsonPath("$.data.totalProcessed").value(2))
                .andExpect(jsonPath("$.data.totalSuccess").value(1))
                .andExpect(jsonPath("$.data.totalFailed").value(1));
    }

    @Test
    @DisplayName("Should accept compatibility bulk endpoint without changing behavior")
    void shouldAcceptCompatibilityBulkEndpoint() throws Exception {
        MultipartFile ok = new MockMultipartFile("files", "ok.txt", "text/plain", "ok".getBytes());
        FileUploadResultRecord success = FileUploadResultRecord.success("ok.txt", "ok.txt", "id1", 2L, "text/plain");
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(success));

        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), Mockito.isNull())).thenReturn(bulk);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(multipart("/api/files/bulk").file((MockMultipartFile) ok))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.stats").doesNotExist())
                .andExpect(jsonPath("$.data.results[0].fileName").value("ok.txt"))
                .andExpect(jsonPath("$.data.results[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.results[0].file.fileName").value("ok.txt"))
                .andExpect(jsonPath("$.data.totalProcessed").value(1))
                .andExpect(jsonPath("$.data.totalSuccess").value(1))
                .andExpect(jsonPath("$.data.totalFailed").value(0));
    }

    @Test
    @DisplayName("Should accept homogeneous metadata and conflictPolicy arrays for bulk compatibility")
    void shouldAcceptHomogeneousBulkCompatibilityOverrides() throws Exception {
        MultipartFile first = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MultipartFile second = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());
        FileUploadResultRecord successA = FileUploadResultRecord.success("a.txt", "a.txt", "id-a", 1L, "text/plain");
        FileUploadResultRecord successB = FileUploadResultRecord.success("b.txt", "b.txt", "id-b", 1L, "text/plain");
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(successA, successB));

        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), argThat(options ->
                options != null
                        && options.nameConflictPolicy() == NameConflictPolicy.RENAME
                        && "finance".equals(options.customMetadata().get("category"))
        ))).thenReturn(bulk);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(
                        multipart("/api/files/bulk")
                                .file((MockMultipartFile) first)
                                .file((MockMultipartFile) second)
                                .param("metadata", "[{\"category\":\"finance\"},{\"category\":\"finance\"}]")
                                .param("conflictPolicy", "[\"RENAME\",\"RENAME\"]")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalProcessed").value(2))
                .andExpect(jsonPath("$.data.totalSuccess").value(2))
                .andExpect(jsonPath("$.data.results[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should reject heterogeneous metadata arrays in bulk compatibility mode")
    void shouldRejectHeterogeneousBulkMetadataArrays() throws Exception {
        MultipartFile first = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MultipartFile second = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(
                        multipart("/api/files/bulk")
                                .file((MockMultipartFile) first)
                                .file((MockMultipartFile) second)
                                .param("metadata", "[{\"category\":\"finance\"},{\"category\":\"hr\"}]")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failure"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_OPTIONS_FORMAT"))
                .andExpect(jsonPath("$.message").value("Erro ao processar opções de upload"));
    }

    @Test
    @DisplayName("Should reject heterogeneous conflictPolicy arrays in bulk compatibility mode")
    void shouldRejectHeterogeneousBulkConflictPolicies() throws Exception {
        MultipartFile first = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MultipartFile second = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(
                        multipart("/api/files/bulk")
                                .file((MockMultipartFile) first)
                                .file((MockMultipartFile) second)
                                .param("conflictPolicy", "[\"RENAME\",\"OVERWRITE\"]")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("failure"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_OPTIONS_FORMAT"))
                .andExpect(jsonPath("$.message").value("Erro ao processar opções de upload"));
    }

    @Test
    @DisplayName("Should expose canonical nested error envelope for bulk error items")
    void shouldExposeCanonicalNestedBulkErrorEnvelope() throws Exception {
        MultipartFile bad = new MockMultipartFile("files", "bad.txt", "text/plain", "bad".getBytes());
        FileUploadResultRecord error = FileUploadResultRecord.error("bad.txt", FileErrorReason.FILE_TOO_LARGE, "Too big");
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(error));

        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), Mockito.isNull())).thenReturn(bulk);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(multipart("/api/files/bulk").file((MockMultipartFile) bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.results[0].fileName").value("bad.txt"))
                .andExpect(jsonPath("$.data.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.status").value("failure"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.stats").doesNotExist())
                .andExpect(jsonPath("$.data.results[0].error.status").value("failure"))
                .andExpect(jsonPath("$.data.results[0].error.message").exists())
                .andExpect(jsonPath("$.data.results[0].error.errors[0].code").value("ARQUIVO_MUITO_GRANDE"))
                .andExpect(jsonPath("$.data.results[0].error.errors[0].message").exists())
                .andExpect(jsonPath("$.data.results[0].error.errors[0].details").exists())
                .andExpect(jsonPath("$.data.totalProcessed").value(1))
                .andExpect(jsonPath("$.data.totalFailed").value(1));
    }

    @Test
    @DisplayName("Should reject bulk upload when no files are provided")
    void shouldRejectBulkUploadWithoutFiles() {
        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(
            null, null, null, null, null, null, null, null, null
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("failure", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Should reject invalid bulk options JSON")
    void shouldRejectInvalidBulkOptionsJson() {
        MultipartFile file = new MockMultipartFile("files", "ok.txt", "text/plain", "ok".getBytes());

        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(
            new MultipartFile[]{file}, "{", null, null, null, null, null, null, null
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_OPTIONS_FORMAT", ((java.util.List<Map<String, Object>>) response.getBody().get("errors")).get(0).get("code"));
    }

    @Test
    @DisplayName("Should reject bulk compatibility arrays with invalid lengths")
    void shouldRejectBulkCompatibilityArraysWithInvalidLengths() {
        MultipartFile first = new MockMultipartFile("files", "a.txt", "text/plain", "a".getBytes());
        MultipartFile second = new MockMultipartFile("files", "b.txt", "text/plain", "b".getBytes());

        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(
            new MultipartFile[]{first, second},
            null,
            "[{\"category\":\"finance\"}]",
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("failure", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Should prefer direct bulk parameters over JSON values")
    void shouldPreferDirectBulkParametersOverJsonValues() {
        MultipartFile file = new MockMultipartFile("files", "ok.txt", "text/plain", "ok".getBytes());
        FileUploadResultRecord success = FileUploadResultRecord.success("ok.txt", "ok.txt", "id1", 2L, "text/plain");
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(success));

        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), argThat(options ->
            options != null
                && options.failFastMode()
                && options.strictValidation()
                && options.maxUploadSizeMb() == 25L
        ))).thenReturn(bulk);

        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(
            new MultipartFile[]{file},
            "{\"failFastMode\":false,\"strictValidation\":false,\"maxUploadSizeMb\":10}",
            null,
            null,
            true,
            true,
            25L,
            null,
            null
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @DisplayName("Should return 415 when all bulk failures are unsupported media")
    void shouldReturnUnsupportedMediaTypeWhenAllBulkFailuresAreUnsupportedMedia() {
        MultipartFile file = new MockMultipartFile("files", "bad.txt", "text/plain", "bad".getBytes());
        FileUploadResultRecord error = FileUploadResultRecord.error("bad.txt", FileErrorReason.MIME_TYPE_MISMATCH, "Bad type");
        BulkUploadResultRecord bulk = BulkUploadResultRecord.fromResults(List.of(error));

        Mockito.when(fileService.uploadMultipleFiles(Mockito.any(), Mockito.any())).thenReturn(bulk);

        ResponseEntity<Map<String, Object>> response = controller.uploadMultipleFiles(
            new MultipartFile[]{file}, null, null, null, null, null, null, null, null
        );

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    }
}
