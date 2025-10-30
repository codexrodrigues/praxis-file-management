package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.audit.SecurityAuditLogger;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.validation.InputValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Name Conflict Policy Tests")
public class NameConflictPolicyTest {

    @Mock(lenient = true)
    private FileStructureValidator fileStructureValidator;

    @Mock(lenient = true)
    private MagicNumberValidator magicNumberValidator;

    @Mock(lenient = true)
    private FileIdMappingService fileIdMappingService;

    @Mock(lenient = true)
    private ThreadSafeFileNamingService fileNamingService;

    @Mock(lenient = true)
    private AtomicFileOperationService atomicFileOperationService;

    @Mock(lenient = true)
    private InputValidationService inputValidationService;

    @Mock(lenient = true)
    private SecurityAuditLogger securityAuditLogger;

    @Mock(lenient = true)
    private UploadAttemptTracker uploadAttemptTracker;

    @Mock(lenient = true)
    private FileUploadMetricsService metricsService;

    private LocalStorageFileService fileService;
    private FileManagementProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        properties = new FileManagementProperties();
        properties.setUploadDir(tempDir.toString());

        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setBlockSuspiciousExtensions(false);
        security.setMagicNumberValidation(false);
        properties.setSecurity(security);

        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(false);
        properties.setVirusScanning(virusScanning);

        FileManagementProperties.AuditLogging auditLogging = new FileManagementProperties.AuditLogging();
        auditLogging.setEnabled(false);
        properties.setAuditLogging(auditLogging);

        fileService = new LocalStorageFileService();

        java.lang.reflect.Field propertiesField = LocalStorageFileService.class.getDeclaredField("fileManagementProperties");
        propertiesField.setAccessible(true);
        propertiesField.set(fileService, properties);

        inject("fileStructureValidator", fileStructureValidator);
        inject("magicNumberValidator", magicNumberValidator);
        inject("fileIdMappingService", fileIdMappingService);
        inject("fileNamingService", fileNamingService);
        inject("atomicFileOperationService", atomicFileOperationService);
        inject("inputValidationService", inputValidationService);
        inject("securityAuditLogger", securityAuditLogger);
        inject("uploadAttemptTracker", uploadAttemptTracker);
        inject("metricsService", metricsService);

        when(inputValidationService.validateUploadFile(any(), any())).thenReturn(
            new InputValidationService.ValidationResult(true, List.of()));
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        when(magicNumberValidator.validateMagicNumbers(any(), any(), any()))
            .thenReturn(new MagicNumberValidator.ValidationResult(true, "Valid magic numbers", "VALID"));
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("generated-id");
        when(atomicFileOperationService.executeAtomicUpload(any(), any(), anyString(), anyString(), any(), any()))
            .thenReturn(new AtomicFileOperationService.UploadTransaction());
        when(fileNamingService.generateUniqueNameForRename(anyString(), any()))
            .thenReturn(new ThreadSafeFileNamingService.UniqueNameResult(
                "renamed.txt", null, 1, ThreadSafeFileNamingService.NamingStrategy.INCREMENTAL));
    }

    private void inject(String field, Object value) throws Exception {
        java.lang.reflect.Field f = LocalStorageFileService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(fileService, value);
    }

    @Test
    void usesDefaultPolicyWhenOptionsMissing() throws Exception {
        properties.setDefaultConflictPolicy(NameConflictPolicy.OVERWRITE);
        Path existing = tempDir.resolve("default.txt");
        Files.write(existing, "existing".getBytes());

        MultipartFile file = new MockMultipartFile("file", "default.txt", "text/plain", "data".getBytes());
        FileUploadResultRecord result = fileService.uploadFile(file, null);

        assertTrue(result.success());
        assertEquals("default.txt", result.serverFilename());
    }

    @Test
    void rejectPolicyReturnsError() throws Exception {
        Path existing = tempDir.resolve("conflict.txt");
        Files.write(existing, "existing".getBytes());

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .nameConflictPolicy(NameConflictPolicy.ERROR)
            .build();

        MultipartFile file = new MockMultipartFile("file", "conflict.txt", "text/plain", "data".getBytes());
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        assertFalse(result.success());
        assertEquals(FileErrorReason.FILE_EXISTS, result.errorReason());
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void overwritePolicySucceeds() throws Exception {
        Path existing = tempDir.resolve("over.txt");
        Files.write(existing, "existing".getBytes());

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .nameConflictPolicy(NameConflictPolicy.OVERWRITE)
            .build();

        MultipartFile file = new MockMultipartFile("file", "over.txt", "text/plain", "data".getBytes());
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        assertTrue(result.success());
        assertEquals("over.txt", result.serverFilename());
    }

    @Test
    void renamePolicyGeneratesNewName() throws Exception {
        Path existing = tempDir.resolve("rename.txt");
        Files.write(existing, "existing".getBytes());

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .nameConflictPolicy(NameConflictPolicy.RENAME)
            .build();

        MultipartFile file = new MockMultipartFile("file", "rename.txt", "text/plain", "data".getBytes());
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        assertTrue(result.success());
        assertEquals("renamed.txt", result.serverFilename());
    }
}
