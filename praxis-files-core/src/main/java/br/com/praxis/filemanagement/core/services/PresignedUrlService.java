package br.com.praxis.filemanagement.core.services;

import org.springframework.stereotype.Service;

/**
 * Proof of concept service for generating pre-signed upload URLs.
 * In real scenarios this would integrate with S3 or GCS SDKs.
 */
@Service
public class PresignedUrlService {

    public String createUploadUrl(String filename) {
        return "https://example.com/upload/" + filename + "?signature=dummy";
    }
}
