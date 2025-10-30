package br.com.praxis.filemanagement.core.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtomicFileOperationServiceTest {

    @Mock
    private FileIdMappingService fileIdMappingService;

    @TempDir
    Path tempDir;

    private AtomicFileOperationService service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.spy(new AtomicFileOperationService());
        ReflectionTestUtils.setField(service, "fileIdMappingService", fileIdMappingService);
    }

    @Test
    void fallsBackToCopyOnCrossDeviceMove() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hi".getBytes());
        Path destination = tempDir.resolve("out.txt");

        doThrow(new AtomicMoveNotSupportedException("src", "dest", "unsupported"))
                .when(service).doAtomicMove(any(), any());
        doThrow(new FileSystemException("src", "dest", "cross-device"))
                .when(service).doStandardMove(any(), any());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
                .thenReturn("id");

        service.executeAtomicUpload(file, destination, "out.txt", "text/plain", tempDir, tempDir);

        assertTrue(Files.exists(destination));
        long leftover = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("upload_"))
                .count();
        assertEquals(0, leftover);
    }

    @Test
    void streamsContentWithoutUsingTransferTo() throws Exception {
        byte[] data = new byte[1024 * 1024]; // 1MB
        MultipartFile file = new MultipartFile() {
            @Override public String getName() { return "file"; }
            @Override public String getOriginalFilename() { return "big.bin"; }
            @Override public String getContentType() { return "application/octet-stream"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return data.length; }
            @Override public byte[] getBytes() { throw new IllegalStateException("should not buffer"); }
            @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(data); }
            @Override public void transferTo(java.io.File dest) { throw new IllegalStateException("transferTo not allowed"); }
        };

        Path destination = tempDir.resolve("big.bin");
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
                .thenReturn("id");

        service.executeAtomicUpload(file, destination, "big.bin", "application/octet-stream", tempDir, tempDir);

        assertTrue(Files.exists(destination));
    }
}
