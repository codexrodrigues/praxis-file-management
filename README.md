# Praxis File Management (repo dedicado)

This is the dedicated repository for the Praxis File Management libraries. It was extracted from the Praxis monorepo and now hosts the full multi-module project here:

- Modules: `praxis-files-api`, `praxis-files-core`, `praxis-files-web`, `praxis-files-starter`, `praxis-files-app`.
- Repository URL: https://github.com/codexrodrigues/praxis-file-management

The Praxis File Management libraries provide a Spring Boot starter that exposes secure file upload endpoints with sensible defaults so new applications can be integrated with minimal configuration.

## Clone e build rápidos

```bash
git clone https://github.com/codexrodrigues/praxis-file-management.git
cd praxis-file-management
mvn -q -DskipTests package
```

## Quick start (5 minutes)

1. Add the starter dependency:
```xml
<dependency>
  <groupId>br.com.praxis</groupId>
  <artifactId>praxis-files-starter</artifactId>
</dependency>
```

2. (Optional) Configure the upload directory:
```properties
file.management.upload-dir=./uploads
```

3. Start your application and upload a file:
```bash
curl -F file=@README.md http://localhost:8080/file-management/upload
```
Expected response: `201 Created`.

## Default behaviour

Out of the box the starter enables common protections while keeping optional integrations disabled:

- Magic number validation is **enabled** to ensure the binary signature matches the declared MIME type.
- Antivirus scanning is **disabled** and can be enabled when a ClamAV service is available.
- Single file uploads are limited to **10&nbsp;MB**.
- Basic rate limiting allows **10 uploads per minute** and **100 per hour**.
- Quota enforcement is **disabled**; configure `file.management.quota` to limit uploads per tenant or user.

## Reading effective configuration

Clients can query the effective upload settings applied by the server through
`GET /api/files/config`. The endpoint returns an envelope with the options the
backend will use if an upload starts in the current context, including bulk
limits, rate limiting and friendly error messages:

```bash
curl http://localhost:8080/api/files/config
```

Example response:

```json
{
  "success": true,
  "timestamp": "2025-08-22T12:34:56.789Z",
  "data": {
    "options": { "nameConflictPolicy": "RENAME" },
    "bulk": { "failFastModeDefault": false },
    "rateLimit": { "enabled": true },
    "quotas": { "enabled": false },
    "messages": {
      "FILE_TOO_LARGE": "Arquivo muito grande.",
      "RATE_LIMIT_EXCEEDED": "Limite de taxa de upload excedido."
    },
    "metadata": { "version": "1.1.0-SNAPSHOT", "locale": "pt-BR" }
  }
}
```

## Configuration properties

All properties are prefixed with `file.management`.

<!-- CONFIG_TABLE:START -->
| Property | Default | Description |
|----------|---------|-------------|
| `file.management.audit-logging` | `new AuditLogging()` | Audit logging settings |
| `file.management.audit-logging.cleanup-interval-minutes` | `60` | Cleanup interval for statistics in minutes |
| `file.management.audit-logging.enabled` | `true` | Whether audit logging is enabled |
| `file.management.audit-logging.include-sensitive-data` | `true` | Whether to include sensitive information in logs (filenames, IPs) |
| `file.management.audit-logging.log-all-security-events` | `true` | Whether to log all security events or only violations |
| `file.management.audit-logging.log-file-access` | `false` | Whether to log file access events |
| `file.management.audit-logging.log-rate-limit-violations` | `false` | Whether to log rate limit violations |
| `file.management.audit-logging.log-successful-uploads` | `true` | Whether to log successful uploads (can be verbose) |
| `file.management.audit-logging.log-virus-scan-results` | `false` | Whether to log virus scan results |
| `file.management.audit-logging.max-error-message-length` | `1000` | Maximum length for error messages in logs |
| `file.management.audit-logging.minimum-severity` | ``LOW`` | Minimum severity level to log (LOW, MEDIUM, HIGH, CRITICAL) |
| `file.management.audit-logging.statistics-retention-hours` | `24` | Retention period for statistics in hours |
| `file.management.audit-logging.use-structured-logging` | `true` | Whether to use structured JSON logging |
| `file.management.bulk-upload` | `new BulkUpload()` | Bulk upload settings |
| `file.management.bulk-upload.enable-bulk-rate-limit` | `true` | Whether to enable bulk-specific rate limiting |
| `file.management.bulk-upload.enable-parallel-processing` | `true` | Whether to enable parallel processing for bulk uploads |
| `file.management.bulk-upload.fail-fast-mode` | `false` | Whether to fail entire batch if any file fails |
| `file.management.bulk-upload.max-bulk-uploads-per-hour` | `10` | Maximum bulk uploads per hour per IP |
| `file.management.bulk-upload.max-concurrent-uploads` | `3` | Maximum number of concurrent uploads in bulk operation |
| `file.management.bulk-upload.max-files-per-batch` | `50` | Maximum number of files in a single bulk upload |
| `file.management.bulk-upload.max-total-size-mb` | `100` | Maximum total size for bulk upload in MB |
| `file.management.bulk-upload.timeout-seconds` | `300` | Timeout for bulk upload operation in seconds |
| `file.management.default-conflict-policy` | `NameConflictPolicy.MAKE_UNIQUE` | Default policy for filename conflicts |
| `file.management.max-file-size-bytes` | `10 * 1024 * 1024` | Maximum allowed file size in bytes (default 10MB) |
| `file.management.monitoring` | `new Monitoring()` | Monitoring settings |
| `file.management.monitoring.alert-on-high-error-rate` | `false` | Whether to alert on high error rates |
| `file.management.monitoring.enabled` | `true` | Whether monitoring endpoints are enabled |
| `file.management.monitoring.health-check-cache-seconds` | `30` | Health check cache duration in seconds |
| `file.management.monitoring.include-detailed-metrics` | `true` | Whether to include detailed metrics in health checks |
| `file.management.monitoring.log-health-check-access` | `false` | Whether to log health check access |
| `file.management.monitoring.monitor-virus-scanner-health` | `false` | Whether to monitor virus scanner health |
| `file.management.monitoring.real-time-alerting` | `false` | Whether to enable real-time alerting |
| `file.management.monitoring.required-role` | ``MONITORING`` | Spring Security role required to access monitoring endpoints |
| `file.management.monitoring.scanner-health-check-interval` | `60` | Scanner health check interval in seconds |
| `file.management.monitoring.track-upload-performance` | `false` | Whether to track upload performance metrics |
| `file.management.quota` | `new Quota()` | Quota settings per tenant/user |
| `file.management.quota.enabled` | `false` | Whether quota enforcement is enabled |
| `file.management.rate-limit` | `new RateLimit()` | Rate limiting settings |
| `file.management.rate-limit.block-on-rate-limit-violation` | `false` | Whether to block IP temporarily on rate limit violations |
| `file.management.rate-limit.enable-per-ip-limiting` | `false` | Whether to enable per-IP rate limiting |
| `file.management.rate-limit.enabled` | `true` | Whether rate limiting is enabled |
| `file.management.rate-limit.max-concurrent-uploads` | `3` | Maximum concurrent uploads per IP |
| `file.management.rate-limit.max-uploads-per-hour` | `100` | Maximum uploads per hour per IP |
| `file.management.rate-limit.max-uploads-per-ip-per-hour` | `50` | Maximum uploads per IP per hour (when per-IP limiting is enabled) |
| `file.management.rate-limit.max-uploads-per-minute` | `10` | Maximum uploads per minute per IP |
| `file.management.rate-limit.trusted-proxies` | `new ArrayList<>()` | List of proxy IPs considered trusted for resolving the real client address |
| `file.management.security` | `new Security()` | Security settings |
| `file.management.security.allowed-mime-types` | `new HashSet<>()` | Globally allowed MIME types (empty means all allowed) |
| `file.management.security.allowed-origins` | `new ArrayList<>()` | Lista de origens permitidas para CORS |
| `file.management.security.block-html-files` | `true` | Whether to block HTML files completely |
| `file.management.security.block-suspicious-extensions` | `false` | Whether to block suspicious file extensions |
| `file.management.security.blocked-mime-types` | `new HashSet<>()` | Globally blocked MIME types |
| `file.management.security.content-type-validation` | ``permissive`` | Content type validation mode (strict, permissive) |
| `file.management.security.cors-enabled` | `true` | Define se o CORS deve ser configurado automaticamente |
| `file.management.security.csrf-enabled` | `false` | Define se o CSRF deve ser habilitado |
| `file.management.security.dangerous-mime-types` | `new HashSet<>()` | Dangerous MIME types that should always be blocked |
| `file.management.security.deep-content-inspection` | `false` | Whether to perform deep content inspection |
| `file.management.security.enable-request-signing` | `false` | Define se a assinatura de requests deve ser habilitada |
| `file.management.security.magic-number-validation` | `true` | Whether to validate magic numbers <p>Default: {@code true} for safer type detection</p> |
| `file.management.security.mandatory-virus-scan` | `false` | Whether virus scanning is mandatory |
| `file.management.security.max-filename-length` | `255` | Maximum filename length allowed |
| `file.management.security.max-request-rate-per-minute` | `100` | Taxa máxima de requests por minuto por IP |
| `file.management.security.permit-actuator-endpoints` | `false` | Define se os endpoints do actuator devem ser permitidos sem autenticação |
| `file.management.security.permit-file-endpoints` | `false` | Define se os endpoints de arquivo devem ser permitidos sem autenticação |
| `file.management.security.permit-health-endpoints` | `true` | Define se os endpoints de health/monitoramento devem ser permitidos sem autenticação |
| `file.management.security.permitted-patterns` | `new ArrayList<>()` | Lista de padrões de URL que devem ser permitidos sem autenticação Exemplo: ["/api/public/**", "/docs/**"] |
| `file.management.security.protected-patterns` | `new ArrayList<>()` | Lista de padrões de URL que sempre requerem autenticação Tem precedência sobre permittedPatterns |
| `file.management.security.require-api-key` | `false` | Define se uma API key é obrigatória |
| `file.management.security.require-authentication-by-default` | `true` | Define se a autenticação é requerida por padrão para todos os endpoints não configurados |
| `file.management.security.script-detection-buffer-size` | `4096` | Buffer size for script detection in bytes (default 4KB) |
| `file.management.security.strict-validation` | `false` | Whether to enable strict validation |
| `file.management.security.suspicious-extensions` | `new HashSet<>()` | List of suspicious file extensions to block when blockSuspiciousExtensions is enabled |
| `file.management.security.use-mime-whitelist` | `false` | Whether to use MIME type whitelist instead of blacklist |
| `file.management.upload-dir` | ``./uploads`` | Upload directory path |
| `file.management.upload-temp-dir` | `System.getProperty(`java.io.tmpdir`)` | Temporary directory used for streaming uploads before moving to final destination |
| `file.management.virus-scanning` | `new VirusScanning()` | Virus scanning settings |
| `file.management.virus-scanning.clamav-host` | ``localhost`` | ClamAV daemon host |
| `file.management.virus-scanning.clamav-port` | `3310` | ClamAV daemon port |
| `file.management.virus-scanning.connection-timeout` | `5000` | Connection timeout in milliseconds |
| `file.management.virus-scanning.enabled` | `false` | Whether virus scanning is enabled Default: false - deve ser explicitamente habilitado em produção |
| `file.management.virus-scanning.fail-on-scanner-unavailable` | `false` | Whether to fail uploads when virus scanning is unavailable Default: false - graceful degradation |
| `file.management.virus-scanning.log-scan-results` | `false` | Whether to log virus scan results |
| `file.management.virus-scanning.max-file-size-bytes` | `10 * 1024 * 1024` | Maximum file size for scanning in bytes (default 10MB) |
| `file.management.virus-scanning.max-scan-retries` | `1` | Maximum number of scan retries on failure |
| `file.management.virus-scanning.quarantine-dir` | ``./quarantine`` | Directory where quarantined files are stored |
| `file.management.virus-scanning.quarantine-enabled` | `false` | Whether infected files should be moved to a quarantine directory |
| `file.management.virus-scanning.read-timeout` | `30000` | Read timeout in milliseconds |
| `file.management.virus-scanning.require-fresh-signatures` | `false` | Whether to require fresh virus signatures |
<!-- CONFIG_TABLE:END -->

These defaults allow the example application in `praxis-files-app` to start without specifying any custom `file.management` properties.

### Quota example

Limit a tenant to a single upload:

```properties
file.management.quota.enabled=true
file.management.quota.tenant.acme=1
```

## Pre-signed uploads (S3/GCS PoC)

A proof-of-concept endpoint generates pre-signed URLs so clients can upload directly to external storage services like S3 or GCS:

```bash
curl -X POST "http://localhost:8080/api/files/upload/presign?filename=test.txt"
```

The response contains a temporary `url` that accepts a `PUT` request with the file contents.

## Headless mode

To use only the core services without exposing HTTP endpoints or Swagger, disable the web layer by setting `praxis.files.web.enabled=false`.
The example application includes an `application-headless.properties` profile demonstrating a fully headless startup:

```
spring.main.web-application-type=none
praxis.files.web.enabled=false
```

Run the sample with `--spring.profiles.active=headless` to bootstrap without controllers or Swagger UI.

## Enterprise guides

- [Enterprise Architecture Guide](guides/enterprise-architecture-guide.md)
- [Security Hardening Guide](guides/security-hardening-guide.md)
- [Performance Tuning Guide](guides/performance-tuning-guide.md)
- [Monitoring & Alerting Guide](guides/monitoring-alerting-guide.md)
- [Disaster Recovery Guide](guides/disaster-recovery-guide.md)
- [Enterprise Visual Reference](guides/enterprise-visuals.md)
