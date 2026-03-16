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
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void rollsBackCreatedFileWhenMappingGenerationFails() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hi".getBytes());
        Path destination = tempDir.resolve("rolled-back.txt");

        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
                .thenThrow(new IllegalStateException("mapping failed"));

        IOException exception = assertThrows(IOException.class,
                () -> service.executeAtomicUpload(file, destination, "rolled-back.txt", "text/plain", tempDir, tempDir));

        assertTrue(exception.getMessage().contains("Atomic upload failed"));
        assertFalse(Files.exists(destination));
        long leftover = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("upload_"))
                .count();
        assertEquals(0, leftover);
    }

    @Test
    void executeWithRetryRetriesRetriableIoExceptions() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String result = service.executeWithRetry(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new IOException("resource busy");
            }
            return "ok";
        }, 3, 1);

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void executeWithRetryStopsOnNonRetriableException() {
        AtomicInteger attempts = new AtomicInteger();

        IOException exception = assertThrows(IOException.class, () ->
                service.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new IOException("permission denied");
                }, 3, 1));

        assertEquals("permission denied", exception.getMessage());
        assertEquals(1, attempts.get());
    }

    @Test
    void executeWithRetryWrapsNonIoExceptionAfterExhaustion() {
        AtomicInteger attempts = new AtomicInteger();

        IOException exception = assertThrows(IOException.class, () ->
                service.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("boom");
                }, 1, 1));

        assertTrue(exception.getMessage().contains("Operation failed after 2 attempts"));
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals(1, attempts.get());
    }

    @Test
    void executeWithRetryPropagatesInterruption() {
        Thread.currentThread().interrupt();
        try {
            IOException exception = assertThrows(IOException.class, () ->
                    service.executeWithRetry(() -> {
                        throw new IOException("temporarily unavailable");
                    }, 1, 50));

            assertEquals("Operation interrupted", exception.getMessage());
            assertInstanceOf(InterruptedException.class, exception.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void transactionTracksStepsCommitAndRollback() throws Exception {
        AtomicFileOperationService.UploadTransaction transaction = new AtomicFileOperationService.UploadTransaction();
        AtomicInteger rolledBack = new AtomicInteger();
        AtomicFileOperationService.TransactionStep step = new AtomicFileOperationService.TransactionStep() {
            @Override
            public void rollback() {
                rolledBack.incrementAndGet();
            }

            @Override
            public String getDescription() {
                return "step";
            }
        };

        transaction.addStep(step);

        assertTrue(transaction.isActive());
        assertEquals(1, transaction.getStepCount());
        assertNotNull(transaction.getTransactionId());
        assertNotNull(transaction.getStartTime());

        transaction.commit();

        assertTrue(transaction.isCommitted());
        assertFalse(transaction.isActive());
        assertThrows(IllegalStateException.class, () -> transaction.addStep(step));
        assertThrows(IllegalStateException.class, transaction::commit);

        AtomicFileOperationService.UploadTransaction rollbackTransaction = new AtomicFileOperationService.UploadTransaction();
        rollbackTransaction.addStep(step);
        rollbackTransaction.rollback();
        rollbackTransaction.rollback();

        assertTrue(rollbackTransaction.isRolledBack());
        assertFalse(rollbackTransaction.isActive());
        assertEquals(1, rolledBack.get());
    }

    @Test
    void checkpointRollsBackStepsInReverseOrder() {
        AtomicInteger order = new AtomicInteger();
        AtomicInteger firstOrder = new AtomicInteger();
        AtomicInteger secondOrder = new AtomicInteger();

        AtomicFileOperationService.TransactionCheckpoint checkpoint = service.createCheckpoint("demo");
        checkpoint.addRollbackStep(step("first", order, firstOrder));
        checkpoint.addRollbackStep(step("second", order, secondOrder));

        checkpoint.rollback();

        assertNotNull(checkpoint.getCheckpointId());
        assertEquals("demo", checkpoint.getDescription());
        assertNotNull(checkpoint.getCreatedAt());
        assertEquals(1, secondOrder.get());
        assertEquals(2, firstOrder.get());
    }

    @Test
    void checkpointContinuesWhenRollbackStepFails() {
        AtomicInteger counter = new AtomicInteger();
        AtomicFileOperationService.TransactionCheckpoint checkpoint = service.createCheckpoint("failure-tolerant");
        checkpoint.addRollbackStep(mockFailingStep());
        checkpoint.addRollbackStep(step("after-failure", counter, counter));

        checkpoint.rollback();

        assertEquals(1, counter.get());
    }

    private AtomicFileOperationService.TransactionStep step(
            String description,
            AtomicInteger sequence,
            AtomicInteger target) {
        return new AtomicFileOperationService.TransactionStep() {
            @Override
            public void rollback() {
                target.set(sequence.incrementAndGet());
            }

            @Override
            public String getDescription() {
                return description;
            }
        };
    }

    private AtomicFileOperationService.TransactionStep mockFailingStep() {
        try {
            AtomicFileOperationService.TransactionStep step = mock(AtomicFileOperationService.TransactionStep.class);
            doThrow(new IOException("rollback failed")).when(step).rollback();
            return step;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
