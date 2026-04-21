package br.com.praxis.filemanagement.core.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresignedUrlServiceTest {

    @Test
    void createsDeterministicDummyUploadUrlByDefault() {
        PresignedUrlService service = new PresignedUrlService(
            "https://example.com/upload/%s?signature=dummy"
        );
        assertEquals(
            "https://example.com/upload/report.pdf?signature=dummy",
            service.createUploadUrl("report.pdf")
        );
    }

    @Test
    void supportsRelativeLocalTargetWhenConfigured() {
        PresignedUrlService service = new PresignedUrlService("/api/files/upload");
        assertEquals("/api/files/upload", service.createUploadUrl("report.pdf"));
    }
}
