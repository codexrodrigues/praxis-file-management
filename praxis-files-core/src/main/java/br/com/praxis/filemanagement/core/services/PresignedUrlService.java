package br.com.praxis.filemanagement.core.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Proof of concept service for generating pre-signed upload URLs.
 * In real scenarios this would integrate with S3 or GCS SDKs.
 */
@Service
public class PresignedUrlService {

    private final String uploadUrlTemplate;

    public PresignedUrlService(
        @Value("${file.management.presign.upload-url-template:https://example.com/upload/%s?signature=dummy}")
        String uploadUrlTemplate
    ) {
        this.uploadUrlTemplate = uploadUrlTemplate;
    }

    public String createUploadUrl(String filename) {
        if (uploadUrlTemplate.contains("%s")) {
            return String.format(uploadUrlTemplate, filename);
        }
        return uploadUrlTemplate;
    }
}
