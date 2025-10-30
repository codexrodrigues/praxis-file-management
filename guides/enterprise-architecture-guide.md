# Enterprise Architecture Guide

This guide outlines deployment patterns for adopting Praxis File Management in corporate environments.

## Deployment patterns

### Monolithic Spring Boot
- Add `praxis-files-starter` to an existing monolith to expose `/file-management` endpoints.
- Reuse the application's security and persistence stack.
- Suitable when teams want minimal operational overhead.

### Dedicated microservice
- Package file management as a standalone Spring Boot service.
- Allows independent scaling and versioning from the core application.
- Ideal when multiple domains upload through a shared service.

### API Gateway integration
- Front the service with a gateway such as Spring Cloud Gateway or Kong.
- Apply authentication and rate limiting before requests reach the upload service.
- Enables centralised routing and cross-cutting policies.

## Extensibility references

- **Per-client quotas**: configure `file.management.quota.{tenantId}` to isolate storage usage.
- **External audit logs**: forward `file_upload` events to a SIEM or data lake for compliance.
- **Cloud storage**: plug in S3 or GCS via the storage abstraction and use the `presign` endpoint for direct browser uploads.
