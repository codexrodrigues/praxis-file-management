package br.com.praxis.filemanagement.core.utils;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServiceMessageAdapterTest {

    @Test
    void createsStandardAndConvenienceErrorRecords() {
        assertRecord(FileServiceMessageAdapter.createStandardErrorRecord(FileErrorReason.EMPTY_FILE, "empty.txt", 0),
                "empty.txt", FileErrorReason.EMPTY_FILE);
        assertRecord(FileServiceMessageAdapter.createRateLimitExceededRecord("rate.txt", 10),
                "rate.txt", FileErrorReason.RATE_LIMIT_EXCEEDED);
        assertRecord(FileServiceMessageAdapter.createEmptyFileRecord("empty.txt", 0),
                "empty.txt", FileErrorReason.EMPTY_FILE);
        assertRecord(FileServiceMessageAdapter.createFileTypeNotAllowedRecord("bad.exe", 10),
                "bad.exe", FileErrorReason.INVALID_TYPE);
        assertRecord(FileServiceMessageAdapter.createFileExistsRecord("dup.txt", 20),
                "dup.txt", FileErrorReason.FILE_EXISTS);
        assertRecord(FileServiceMessageAdapter.createPathTraversalRecord("../bad.txt", 20),
                "../bad.txt", FileErrorReason.INVALID_PATH);
    }

    @Test
    void createsGenericAndSpecializedErrorRecords() {
        assertRecord(FileServiceMessageAdapter.createGenericErrorRecord("CONFIGURATION_ERROR", "cfg.txt", 10),
                "cfg.txt", FileErrorReason.UNKNOWN_ERROR);
        assertRecord(FileServiceMessageAdapter.createConfigurationErrorRecord("cfg.txt", 10),
                "cfg.txt", FileErrorReason.UNKNOWN_ERROR);
        assertRecord(FileServiceMessageAdapter.createFileAnalysisErrorRecord("analysis.txt", 10),
                "analysis.txt", FileErrorReason.UNKNOWN_ERROR);
        assertRecord(FileServiceMessageAdapter.createVirusScanErrorRecord("virus.txt", 10),
                "virus.txt", FileErrorReason.UNKNOWN_ERROR);
        assertRecord(FileServiceMessageAdapter.createFileStructureErrorRecord("archive.zip", 10),
                "archive.zip", FileErrorReason.UNKNOWN_ERROR);
        assertRecord(FileServiceMessageAdapter.createFileStoreErrorRecord("store.txt", 10),
                "store.txt", FileErrorReason.UNKNOWN_ERROR);
    }

    @Test
    void createsRecordsForFileSizeAndMalwareScenarios() {
        FileUploadResultRecord sizeExceeded = FileServiceMessageAdapter.createFileSizeExceededRecord(11, 10, "big.txt");
        FileUploadResultRecord malware = FileServiceMessageAdapter.createMalwareDetectedRecord("EICAR", "infected.txt", 12);

        assertRecord(sizeExceeded, "big.txt", FileErrorReason.FILE_TOO_LARGE);
        assertRecord(malware, "infected.txt", FileErrorReason.MALWARE_DETECTED);
        assertEquals("Malware detectado no arquivo", malware.errorMessage());
    }

    private void assertRecord(FileUploadResultRecord record, String filename, FileErrorReason reason) {
        assertEquals(filename, record.originalFilename());
        assertEquals(reason, record.errorReason());
        assertTrue(record.errorMessage() != null && !record.errorMessage().isBlank());
    }
}
