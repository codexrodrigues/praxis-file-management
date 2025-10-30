package br.com.praxis.filemanagement.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;

/**
 * Configuration properties for file management.
 * Allows external configuration via application.properties or application.yml
 */
@ConfigurationProperties(prefix = "file.management")
@Validated
public class FileManagementProperties {

    /**
     * Upload directory path
     */
    private String uploadDir = "./uploads";

    /**
     * Temporary directory used for streaming uploads before moving to final destination
     */
    private String uploadTempDir = System.getProperty("java.io.tmpdir");

    /**
     * Maximum allowed file size in bytes (default 10MB)
     */
    private long maxFileSizeBytes = 10 * 1024 * 1024;

    /**
     * Default policy for filename conflicts
     */
    private NameConflictPolicy defaultConflictPolicy = NameConflictPolicy.MAKE_UNIQUE;

    /**
     * Security settings
     */
    private Security security = new Security();

    /**
     * Rate limiting settings
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * Quota settings per tenant/user
     */
    private Quota quota = new Quota();

    /**
     * Virus scanning settings
     */
    private VirusScanning virusScanning = new VirusScanning();

    /**
     * Audit logging settings
     */
    private AuditLogging auditLogging = new AuditLogging();

    /**
     * Monitoring settings
     */
    private Monitoring monitoring = new Monitoring();

    /**
     * Bulk upload settings
     */
    private BulkUpload bulkUpload = new BulkUpload();

    public static class Quota {
        /**
         * Whether quota enforcement is enabled
         */
        private boolean enabled = false;

        /**
         * Maximum uploads allowed per tenant identifier
         */
        private Map<String, Integer> tenant = new HashMap<>();

        /**
         * Maximum uploads allowed per user identifier
         */
        private Map<String, Integer> user = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, Integer> getTenant() {
            return tenant;
        }

        public void setTenant(Map<String, Integer> tenant) {
            this.tenant = tenant;
        }

        public Map<String, Integer> getUser() {
            return user;
        }

        public void setUser(Map<String, Integer> user) {
            this.user = user;
        }

        /**
         * Validate quota configuration
         */
        public void validate() {
            tenant.values().forEach(v -> {
                if (v <= 0) {
                    throw new IllegalArgumentException("Tenant quota must be positive, got: " + v);
                }
            });
            user.values().forEach(v -> {
                if (v <= 0) {
                    throw new IllegalArgumentException("User quota must be positive, got: " + v);
                }
            });
        }
    }

    public static class Security {
        /**
         * Whether to block HTML files completely
         */
        private boolean blockHtmlFiles = true;

        /**
         * Buffer size for script detection in bytes (default 4KB)
         */
        private int scriptDetectionBufferSize = 4096;

        /**
         * Globally allowed MIME types (empty means all allowed)
         */
        private Set<String> allowedMimeTypes = new HashSet<>();

        /**
         * Globally blocked MIME types
         */
        private Set<String> blockedMimeTypes = new HashSet<>();

        /**
         * Dangerous MIME types that should always be blocked
         */
        private Set<String> dangerousMimeTypes = new HashSet<>();

        /**
         * Maximum filename length allowed
         */
        private int maxFilenameLength = 255;

        /**
         * Whether to block suspicious file extensions
         */
        private boolean blockSuspiciousExtensions = false;

        /**
         * List of suspicious file extensions to block when blockSuspiciousExtensions is enabled
         */
        private Set<String> suspiciousExtensions = new HashSet<>();

        /**
         * Whether to perform deep content inspection
         */
        private boolean deepContentInspection = false;

        /**
         * Whether virus scanning is mandatory
         */
        private boolean mandatoryVirusScan = false;

        /**
         * Whether to use MIME type whitelist instead of blacklist
         */
        private boolean useMimeWhitelist = false;

        /**
         * Whether to enable strict validation
         */
        private boolean strictValidation = false;

        /**
         * Content type validation mode (strict, permissive)
         */
        private String contentTypeValidation = "permissive";

        /**
         * Whether to validate magic numbers
         * <p>Default: {@code true} for safer type detection</p>
         */
        private boolean magicNumberValidation = true;

        public Security() {
            // Initialize with commonly dangerous MIME types
            dangerousMimeTypes.add("text/html");
            dangerousMimeTypes.add("application/x-httpd-php");
            dangerousMimeTypes.add("application/x-php");
            dangerousMimeTypes.add("text/x-php");
            dangerousMimeTypes.add("application/x-jsp");
            dangerousMimeTypes.add("text/x-jsp");
            dangerousMimeTypes.add("application/x-msdownload");
            dangerousMimeTypes.add("application/x-executable");
            dangerousMimeTypes.add("application/x-sh");
            dangerousMimeTypes.add("application/x-csh");
            dangerousMimeTypes.add("application/x-shellscript");
            dangerousMimeTypes.add("application/java-vm");
            dangerousMimeTypes.add("application/x-java-applet");
            dangerousMimeTypes.add("application/x-mach-binary");
            dangerousMimeTypes.add("application/x-elf");

            // Initialize with commonly suspicious file extensions
            suspiciousExtensions.add("exe");
            suspiciousExtensions.add("bat");
            suspiciousExtensions.add("cmd");
            suspiciousExtensions.add("com");
            suspiciousExtensions.add("pif");
            suspiciousExtensions.add("scr");
            suspiciousExtensions.add("vbs");
            suspiciousExtensions.add("vbe");
            suspiciousExtensions.add("js");
            suspiciousExtensions.add("jse");
            suspiciousExtensions.add("ws");
            suspiciousExtensions.add("wsf");
            suspiciousExtensions.add("wsc");
            suspiciousExtensions.add("wsh");
            suspiciousExtensions.add("ps1");
            suspiciousExtensions.add("ps1xml");
            suspiciousExtensions.add("ps2");
            suspiciousExtensions.add("ps2xml");
            suspiciousExtensions.add("psc1");
            suspiciousExtensions.add("psc2");
            suspiciousExtensions.add("msh");
            suspiciousExtensions.add("msh1");
            suspiciousExtensions.add("msh2");
            suspiciousExtensions.add("mshxml");
            suspiciousExtensions.add("msh1xml");
            suspiciousExtensions.add("msh2xml");
            suspiciousExtensions.add("sh");
            suspiciousExtensions.add("bash");
            suspiciousExtensions.add("zsh");
            suspiciousExtensions.add("csh");
            suspiciousExtensions.add("tcsh");
            suspiciousExtensions.add("ksh");
            suspiciousExtensions.add("fish");
            suspiciousExtensions.add("php");
            suspiciousExtensions.add("php3");
            suspiciousExtensions.add("php4");
            suspiciousExtensions.add("php5");
            suspiciousExtensions.add("phtml");
            suspiciousExtensions.add("jsp");
            suspiciousExtensions.add("asp");
            suspiciousExtensions.add("aspx");
            suspiciousExtensions.add("cgi");
            suspiciousExtensions.add("pl");
            suspiciousExtensions.add("py");
            suspiciousExtensions.add("rb");
            suspiciousExtensions.add("jar");
            suspiciousExtensions.add("class");
            suspiciousExtensions.add("dex");
            suspiciousExtensions.add("apk");
            suspiciousExtensions.add("app");
            suspiciousExtensions.add("dmg");
            suspiciousExtensions.add("pkg");
            suspiciousExtensions.add("deb");
            suspiciousExtensions.add("rpm");
            suspiciousExtensions.add("msi");
            suspiciousExtensions.add("msix");
            suspiciousExtensions.add("appx");
            suspiciousExtensions.add("xap");
            suspiciousExtensions.add("ipa");
        }

        // Getters and setters
        public boolean isBlockHtmlFiles() {
            return blockHtmlFiles;
        }

        public void setBlockHtmlFiles(boolean blockHtmlFiles) {
            this.blockHtmlFiles = blockHtmlFiles;
        }

        public int getScriptDetectionBufferSize() {
            return scriptDetectionBufferSize;
        }

        public void setScriptDetectionBufferSize(int scriptDetectionBufferSize) {
            this.scriptDetectionBufferSize = scriptDetectionBufferSize;
        }

        public Set<String> getAllowedMimeTypes() {
            return allowedMimeTypes;
        }

        public void setAllowedMimeTypes(Set<String> allowedMimeTypes) {
            this.allowedMimeTypes = allowedMimeTypes;
        }

        public Set<String> getBlockedMimeTypes() {
            return blockedMimeTypes;
        }

        public void setBlockedMimeTypes(Set<String> blockedMimeTypes) {
            this.blockedMimeTypes = blockedMimeTypes;
        }

        public Set<String> getDangerousMimeTypes() {
            return dangerousMimeTypes;
        }

        public void setDangerousMimeTypes(Set<String> dangerousMimeTypes) {
            this.dangerousMimeTypes = dangerousMimeTypes;
        }

        public int getMaxFilenameLength() {
            return maxFilenameLength;
        }

        public void setMaxFilenameLength(int maxFilenameLength) {
            this.maxFilenameLength = maxFilenameLength;
        }

        public boolean isBlockSuspiciousExtensions() {
            return blockSuspiciousExtensions;
        }

        public void setBlockSuspiciousExtensions(boolean blockSuspiciousExtensions) {
            this.blockSuspiciousExtensions = blockSuspiciousExtensions;
        }

        public boolean isDeepContentInspection() {
            return deepContentInspection;
        }

        public void setDeepContentInspection(boolean deepContentInspection) {
            this.deepContentInspection = deepContentInspection;
        }

        public boolean isMandatoryVirusScan() {
            return mandatoryVirusScan;
        }

        public void setMandatoryVirusScan(boolean mandatoryVirusScan) {
            this.mandatoryVirusScan = mandatoryVirusScan;
        }

        public boolean isUseMimeWhitelist() {
            return useMimeWhitelist;
        }

        public void setUseMimeWhitelist(boolean useMimeWhitelist) {
            this.useMimeWhitelist = useMimeWhitelist;
        }

        public boolean isStrictValidation() {
            return strictValidation;
        }

        public void setStrictValidation(boolean strictValidation) {
            this.strictValidation = strictValidation;
        }

        public String getContentTypeValidation() {
            return contentTypeValidation;
        }

        public void setContentTypeValidation(String contentTypeValidation) {
            this.contentTypeValidation = contentTypeValidation;
        }

        public boolean isMagicNumberValidation() {
            return magicNumberValidation;
        }

        public void setMagicNumberValidation(boolean magicNumberValidation) {
            this.magicNumberValidation = magicNumberValidation;
        }

        public Set<String> getSuspiciousExtensions() {
            return suspiciousExtensions;
        }

        public void setSuspiciousExtensions(Set<String> suspiciousExtensions) {
            this.suspiciousExtensions = suspiciousExtensions;
        }

        /**
         * Valida as configurações de segurança
         */
        public void validate() {
            if (scriptDetectionBufferSize <= 0) {
                throw new IllegalArgumentException("Script detection buffer size must be positive, got: " + scriptDetectionBufferSize);
            }
            if (scriptDetectionBufferSize > 65536) { // 64KB max
                throw new IllegalArgumentException("Script detection buffer size must be at most 64KB, got: " + scriptDetectionBufferSize);
            }
            if (maxFilenameLength <= 0) {
                throw new IllegalArgumentException("Max filename length must be positive, got: " + maxFilenameLength);
            }
            if (maxFilenameLength > 1000) {
                throw new IllegalArgumentException("Max filename length must be at most 1000 characters, got: " + maxFilenameLength);
            }

            // Validar content type validation
            if (contentTypeValidation != null &&
                !contentTypeValidation.equals("strict") &&
                !contentTypeValidation.equals("permissive")) {
                throw new IllegalArgumentException("Content type validation must be 'strict' or 'permissive', got: " + contentTypeValidation);
            }

            // Validar configurações de MIME types
            if (useMimeWhitelist && (allowedMimeTypes == null || allowedMimeTypes.isEmpty())) {
                throw new IllegalArgumentException("When using MIME whitelist, allowed MIME types cannot be empty");
            }

            // Validar conflitos de configuração
            if (mandatoryVirusScan && !deepContentInspection) {
                throw new IllegalArgumentException("Mandatory virus scan requires deep content inspection to be enabled");
            }
        }
    }

    public static class RateLimit {
        /**
         * Whether rate limiting is enabled
         */
        private boolean enabled = true;

        /**
         * Maximum uploads per minute per IP
         */
        private int maxUploadsPerMinute = 10;

        /**
         * Maximum uploads per hour per IP
         */
        private int maxUploadsPerHour = 100;

        /**
         * Maximum concurrent uploads per IP
         */
        private int maxConcurrentUploads = 3;

        /**
         * Whether to enable per-IP rate limiting
         */
        private boolean enablePerIpLimiting = false;

        /**
         * Maximum uploads per IP per hour (when per-IP limiting is enabled)
         */
        private int maxUploadsPerIpPerHour = 50;

        /**
         * Whether to block IP temporarily on rate limit violations
         */
        private boolean blockOnRateLimitViolation = false;

        /**
         * List of proxy IPs considered trusted for resolving the real client address
         */
        private List<String> trustedProxies = new ArrayList<>();

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxUploadsPerMinute() {
            return maxUploadsPerMinute;
        }

        public void setMaxUploadsPerMinute(int maxUploadsPerMinute) {
            this.maxUploadsPerMinute = maxUploadsPerMinute;
        }

        public int getMaxUploadsPerHour() {
            return maxUploadsPerHour;
        }

        public void setMaxUploadsPerHour(int maxUploadsPerHour) {
            this.maxUploadsPerHour = maxUploadsPerHour;
        }

        public int getMaxConcurrentUploads() {
            return maxConcurrentUploads;
        }

        public void setMaxConcurrentUploads(int maxConcurrentUploads) {
            this.maxConcurrentUploads = maxConcurrentUploads;
        }

        public boolean isEnablePerIpLimiting() {
            return enablePerIpLimiting;
        }

        public void setEnablePerIpLimiting(boolean enablePerIpLimiting) {
            this.enablePerIpLimiting = enablePerIpLimiting;
        }

        public int getMaxUploadsPerIpPerHour() {
            return maxUploadsPerIpPerHour;
        }

        public void setMaxUploadsPerIpPerHour(int maxUploadsPerIpPerHour) {
            this.maxUploadsPerIpPerHour = maxUploadsPerIpPerHour;
        }

        public boolean isBlockOnRateLimitViolation() {
            return blockOnRateLimitViolation;
        }

        public void setBlockOnRateLimitViolation(boolean blockOnRateLimitViolation) {
            this.blockOnRateLimitViolation = blockOnRateLimitViolation;
        }

        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }

        /**
         * Valida as configurações de rate limiting
         */
        public void validate() {
            if (maxUploadsPerMinute <= 0) {
                throw new IllegalArgumentException("Max uploads per minute must be positive, got: " + maxUploadsPerMinute);
            }
            if (maxUploadsPerHour <= 0) {
                throw new IllegalArgumentException("Max uploads per hour must be positive, got: " + maxUploadsPerHour);
            }
            if (maxConcurrentUploads <= 0) {
                throw new IllegalArgumentException("Max concurrent uploads must be positive, got: " + maxConcurrentUploads);
            }
            if (enablePerIpLimiting && maxUploadsPerIpPerHour <= 0) {
                throw new IllegalArgumentException("Max uploads per IP per hour must be positive when per-IP limiting is enabled, got: " + maxUploadsPerIpPerHour);
            }

            // Validar consistência entre limites
            if (maxUploadsPerMinute > maxUploadsPerHour) {
                throw new IllegalArgumentException("Max uploads per minute (" + maxUploadsPerMinute + ") cannot be greater than max uploads per hour (" + maxUploadsPerHour + ")");
            }
        }
    }

    public static class VirusScanning {
        /**
         * Whether virus scanning is enabled
         * Default: false - deve ser explicitamente habilitado em produção
         */
        private boolean enabled = false;

        /**
         * ClamAV daemon host
         */
        private String clamavHost = "localhost";

        /**
         * ClamAV daemon port
         */
        private int clamavPort = 3310;

        /**
         * Connection timeout in milliseconds
         */
        private int connectionTimeout = 5000;

        /**
         * Read timeout in milliseconds
         */
        private int readTimeout = 30000;

        /**
         * Maximum file size for scanning in bytes (default 10MB)
         */
        private long maxFileSizeBytes = 10 * 1024 * 1024;

        /**
         * Whether to fail uploads when virus scanning is unavailable
         * Default: false - graceful degradation
         */
        private boolean failOnScannerUnavailable = false;

        /**
         * Whether infected files should be moved to a quarantine directory
         */
        private boolean quarantineEnabled = false;

        /**
         * Directory where quarantined files are stored
         */
        private String quarantineDir = "./quarantine";

        /**
         * Whether to log virus scan results
         */
        private boolean logScanResults = false;

        /**
         * Whether to require fresh virus signatures
         */
        private boolean requireFreshSignatures = false;

        /**
         * Maximum number of scan retries on failure
         */
        private int maxScanRetries = 1;

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClamavHost() {
            return clamavHost;
        }

        public void setClamavHost(String clamavHost) {
            this.clamavHost = clamavHost;
        }

        public int getClamavPort() {
            return clamavPort;
        }

        public void setClamavPort(int clamavPort) {
            this.clamavPort = clamavPort;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public boolean isFailOnScannerUnavailable() {
            return failOnScannerUnavailable;
        }

        public void setFailOnScannerUnavailable(boolean failOnScannerUnavailable) {
            this.failOnScannerUnavailable = failOnScannerUnavailable;
        }

        public boolean isQuarantineEnabled() {
            return quarantineEnabled;
        }

        public void setQuarantineEnabled(boolean quarantineEnabled) {
            this.quarantineEnabled = quarantineEnabled;
        }

        public String getQuarantineDir() {
            return quarantineDir;
        }

        public void setQuarantineDir(String quarantineDir) {
            this.quarantineDir = quarantineDir;
        }

        public boolean isLogScanResults() {
            return logScanResults;
        }

        public void setLogScanResults(boolean logScanResults) {
            this.logScanResults = logScanResults;
        }

        public boolean isRequireFreshSignatures() {
            return requireFreshSignatures;
        }

        public void setRequireFreshSignatures(boolean requireFreshSignatures) {
            this.requireFreshSignatures = requireFreshSignatures;
        }

        public int getMaxScanRetries() {
            return maxScanRetries;
        }

        public void setMaxScanRetries(int maxScanRetries) {
            this.maxScanRetries = maxScanRetries;
        }

        /**
         * Validate configuration values
         */
        public void validate() {
            if (connectionTimeout < 0) {
                throw new IllegalArgumentException("Connection timeout must be non-negative, got: " + connectionTimeout);
            }
            if (readTimeout < 0) {
                throw new IllegalArgumentException("Read timeout must be non-negative, got: " + readTimeout);
            }
            if (maxFileSizeBytes < 0) {
                throw new IllegalArgumentException("Max file size must be non-negative, got: " + maxFileSizeBytes);
            }
            if (clamavPort < 1 || clamavPort > 65535) {
                throw new IllegalArgumentException("ClamAV port must be between 1-65535, got: " + clamavPort);
            }
            if (clamavHost == null || clamavHost.trim().isEmpty()) {
                throw new IllegalArgumentException("ClamAV host cannot be null or empty");
            }
        }
    }

    public static class AuditLogging {
        /**
         * Whether audit logging is enabled
         */
        private boolean enabled = true;

        /**
         * Whether to log successful uploads (can be verbose)
         */
        private boolean logSuccessfulUploads = true;

        /**
         * Whether to log all security events or only violations
         */
        private boolean logAllSecurityEvents = true;

        /**
         * Minimum severity level to log (LOW, MEDIUM, HIGH, CRITICAL)
         */
        private String minimumSeverity = "LOW";

        /**
         * Whether to include sensitive information in logs (filenames, IPs)
         */
        private boolean includeSensitiveData = true;

        /**
         * Whether to use structured JSON logging
         */
        private boolean useStructuredLogging = true;

        /**
         * Maximum length for error messages in logs
         */
        private int maxErrorMessageLength = 1000;

        /**
         * Cleanup interval for statistics in minutes
         */
        private int cleanupIntervalMinutes = 60;

        /**
         * Retention period for statistics in hours
         */
        private int statisticsRetentionHours = 24;

        /**
         * Whether to log rate limit violations
         */
        private boolean logRateLimitViolations = false;

        /**
         * Whether to log virus scan results
         */
        private boolean logVirusScanResults = false;

        /**
         * Whether to log file access events
         */
        private boolean logFileAccess = false;

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogSuccessfulUploads() {
            return logSuccessfulUploads;
        }

        public void setLogSuccessfulUploads(boolean logSuccessfulUploads) {
            this.logSuccessfulUploads = logSuccessfulUploads;
        }

        public boolean isLogAllSecurityEvents() {
            return logAllSecurityEvents;
        }

        public void setLogAllSecurityEvents(boolean logAllSecurityEvents) {
            this.logAllSecurityEvents = logAllSecurityEvents;
        }

        public String getMinimumSeverity() {
            return minimumSeverity;
        }

        public void setMinimumSeverity(String minimumSeverity) {
            this.minimumSeverity = minimumSeverity;
        }

        public boolean isIncludeSensitiveData() {
            return includeSensitiveData;
        }

        public void setIncludeSensitiveData(boolean includeSensitiveData) {
            this.includeSensitiveData = includeSensitiveData;
        }

        public boolean isUseStructuredLogging() {
            return useStructuredLogging;
        }

        public void setUseStructuredLogging(boolean useStructuredLogging) {
            this.useStructuredLogging = useStructuredLogging;
        }

        public int getMaxErrorMessageLength() {
            return maxErrorMessageLength;
        }

        public void setMaxErrorMessageLength(int maxErrorMessageLength) {
            this.maxErrorMessageLength = maxErrorMessageLength;
        }

        public int getCleanupIntervalMinutes() {
            return cleanupIntervalMinutes;
        }

        public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
        }

        public int getStatisticsRetentionHours() {
            return statisticsRetentionHours;
        }

        public void setStatisticsRetentionHours(int statisticsRetentionHours) {
            this.statisticsRetentionHours = statisticsRetentionHours;
        }

        public boolean isLogRateLimitViolations() {
            return logRateLimitViolations;
        }

        public void setLogRateLimitViolations(boolean logRateLimitViolations) {
            this.logRateLimitViolations = logRateLimitViolations;
        }

        public boolean isLogVirusScanResults() {
            return logVirusScanResults;
        }

        public void setLogVirusScanResults(boolean logVirusScanResults) {
            this.logVirusScanResults = logVirusScanResults;
        }

        public boolean isLogFileAccess() {
            return logFileAccess;
        }

        public void setLogFileAccess(boolean logFileAccess) {
            this.logFileAccess = logFileAccess;
        }

        /**
         * Validate configuration values
         */
        public void validate() {
            if (maxErrorMessageLength < 100) {
                throw new IllegalArgumentException("Max error message length must be at least 100, got: " + maxErrorMessageLength);
            }
            if (maxErrorMessageLength > 10000) {
                throw new IllegalArgumentException("Max error message length must be at most 10000, got: " + maxErrorMessageLength);
            }

            // Validate severity levels
            String[] validSeverities = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
            boolean validSeverity = false;
            for (String valid : validSeverities) {
                if (valid.equals(minimumSeverity)) {
                    validSeverity = true;
                    break;
                }
            }
            if (!validSeverity) {
                throw new IllegalArgumentException("Invalid minimum severity: " + minimumSeverity +
                    ". Valid values are: LOW, MEDIUM, HIGH, CRITICAL");
            }

            // Validate cleanup intervals
            if (cleanupIntervalMinutes < 1) {
                throw new IllegalArgumentException("Cleanup interval must be at least 1 minute, got: " + cleanupIntervalMinutes);
            }
            if (cleanupIntervalMinutes > 1440) { // 24 hours
                throw new IllegalArgumentException("Cleanup interval must be at most 1440 minutes (24 hours), got: " + cleanupIntervalMinutes);
            }

            if (statisticsRetentionHours < 1) {
                throw new IllegalArgumentException("Statistics retention must be at least 1 hour, got: " + statisticsRetentionHours);
            }
            if (statisticsRetentionHours > 168) { // 7 days
                throw new IllegalArgumentException("Statistics retention must be at most 168 hours (7 days), got: " + statisticsRetentionHours);
            }
        }
    }

    public static class Monitoring {
        /**
         * Whether monitoring endpoints are enabled
         */
        private boolean enabled = true;

        /**
         * Health check cache duration in seconds
         */
        private int healthCheckCacheSeconds = 30;

        /**
         * Whether to include detailed metrics in health checks
         */
        private boolean includeDetailedMetrics = true;

        /**
         * Whether to log health check access
         */
        private boolean logHealthCheckAccess = false;

        /**
         * Whether to track upload performance metrics
         */
        private boolean trackUploadPerformance = false;

        /**
         * Whether to alert on high error rates
         */
        private boolean alertOnHighErrorRate = false;

        /**
         * Whether to monitor virus scanner health
         */
        private boolean monitorVirusScannerHealth = false;

        /**
         * Scanner health check interval in seconds
         */
        private int scannerHealthCheckInterval = 60;

        /**
         * Whether to enable real-time alerting
         */
        private boolean realTimeAlerting = false;

        /**
         * Spring Security role required to access monitoring endpoints
         */
        private String requiredRole = "MONITORING";

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getHealthCheckCacheSeconds() {
            return healthCheckCacheSeconds;
        }

        public void setHealthCheckCacheSeconds(int healthCheckCacheSeconds) {
            this.healthCheckCacheSeconds = healthCheckCacheSeconds;
        }

        public boolean isIncludeDetailedMetrics() {
            return includeDetailedMetrics;
        }

        public void setIncludeDetailedMetrics(boolean includeDetailedMetrics) {
            this.includeDetailedMetrics = includeDetailedMetrics;
        }

        public boolean isLogHealthCheckAccess() {
            return logHealthCheckAccess;
        }

        public void setLogHealthCheckAccess(boolean logHealthCheckAccess) {
            this.logHealthCheckAccess = logHealthCheckAccess;
        }

        public boolean isTrackUploadPerformance() {
            return trackUploadPerformance;
        }

        public void setTrackUploadPerformance(boolean trackUploadPerformance) {
            this.trackUploadPerformance = trackUploadPerformance;
        }

        public boolean isAlertOnHighErrorRate() {
            return alertOnHighErrorRate;
        }

        public void setAlertOnHighErrorRate(boolean alertOnHighErrorRate) {
            this.alertOnHighErrorRate = alertOnHighErrorRate;
        }

        public boolean isMonitorVirusScannerHealth() {
            return monitorVirusScannerHealth;
        }

        public void setMonitorVirusScannerHealth(boolean monitorVirusScannerHealth) {
            this.monitorVirusScannerHealth = monitorVirusScannerHealth;
        }

        public int getScannerHealthCheckInterval() {
            return scannerHealthCheckInterval;
        }

        public void setScannerHealthCheckInterval(int scannerHealthCheckInterval) {
            this.scannerHealthCheckInterval = scannerHealthCheckInterval;
        }

        public boolean isRealTimeAlerting() {
            return realTimeAlerting;
        }

        public void setRealTimeAlerting(boolean realTimeAlerting) {
            this.realTimeAlerting = realTimeAlerting;
        }

        public String getRequiredRole() {
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }

        /**
         * Validate configuration values
         */
        public void validate() {
            if (healthCheckCacheSeconds < 0) {
                throw new IllegalArgumentException("Health check cache duration must be non-negative, got: " + healthCheckCacheSeconds);
            }
            if (healthCheckCacheSeconds > 300) { // 5 minutes max
                throw new IllegalArgumentException("Health check cache duration must be at most 300 seconds, got: " + healthCheckCacheSeconds);
            }
        }
    }

    public static class BulkUpload {
        /**
         * Maximum total size for bulk upload in MB
         */
        private long maxTotalSizeMb = 100;

        /**
         * Maximum number of concurrent uploads in bulk operation
         */
        private int maxConcurrentUploads = 3;

        /**
         * Timeout for bulk upload operation in seconds
         */
        private int timeoutSeconds = 300;

        /**
         * Whether to enable parallel processing for bulk uploads
         */
        private boolean enableParallelProcessing = true;

        /**
         * Maximum number of files in a single bulk upload
         */
        private int maxFilesPerBatch = 50;

        /**
         * Whether to fail entire batch if any file fails
         */
        private boolean failFastMode = false;

        /**
         * Whether to enable bulk-specific rate limiting
         */
        private boolean enableBulkRateLimit = true;

        /**
         * Maximum bulk uploads per hour per IP
         */
        private int maxBulkUploadsPerHour = 10;

        // Getters and setters
        public long getMaxTotalSizeMb() {
            return maxTotalSizeMb;
        }

        public void setMaxTotalSizeMb(long maxTotalSizeMb) {
            this.maxTotalSizeMb = maxTotalSizeMb;
        }

        public int getMaxConcurrentUploads() {
            return maxConcurrentUploads;
        }

        public void setMaxConcurrentUploads(int maxConcurrentUploads) {
            this.maxConcurrentUploads = maxConcurrentUploads;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isEnableParallelProcessing() {
            return enableParallelProcessing;
        }

        public void setEnableParallelProcessing(boolean enableParallelProcessing) {
            this.enableParallelProcessing = enableParallelProcessing;
        }

        public int getMaxFilesPerBatch() {
            return maxFilesPerBatch;
        }

        public void setMaxFilesPerBatch(int maxFilesPerBatch) {
            this.maxFilesPerBatch = maxFilesPerBatch;
        }

        public boolean isFailFastMode() {
            return failFastMode;
        }

        public void setFailFastMode(boolean failFastMode) {
            this.failFastMode = failFastMode;
        }

        public boolean isEnableBulkRateLimit() {
            return enableBulkRateLimit;
        }

        public void setEnableBulkRateLimit(boolean enableBulkRateLimit) {
            this.enableBulkRateLimit = enableBulkRateLimit;
        }

        public int getMaxBulkUploadsPerHour() {
            return maxBulkUploadsPerHour;
        }

        public void setMaxBulkUploadsPerHour(int maxBulkUploadsPerHour) {
            this.maxBulkUploadsPerHour = maxBulkUploadsPerHour;
        }

        /**
         * Validate bulk upload configuration values
         */
        public void validate() {
            if (maxTotalSizeMb <= 0) {
                throw new IllegalArgumentException("Max total size must be positive, got: " + maxTotalSizeMb);
            }
            if (maxTotalSizeMb > 1024) { // 1GB max
                throw new IllegalArgumentException("Max total size must be at most 1GB, got: " + maxTotalSizeMb + "MB");
            }
            if (maxConcurrentUploads <= 0) {
                throw new IllegalArgumentException("Max concurrent uploads must be positive, got: " + maxConcurrentUploads);
            }
            if (maxConcurrentUploads > 20) {
                throw new IllegalArgumentException("Max concurrent uploads must be at most 20, got: " + maxConcurrentUploads);
            }
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("Timeout must be positive, got: " + timeoutSeconds);
            }
            if (timeoutSeconds > 3600) { // 1 hour max
                throw new IllegalArgumentException("Timeout must be at most 3600 seconds (1 hour), got: " + timeoutSeconds);
            }
            if (maxFilesPerBatch <= 0) {
                throw new IllegalArgumentException("Max files per batch must be positive, got: " + maxFilesPerBatch);
            }
            if (maxFilesPerBatch > 100) {
                throw new IllegalArgumentException("Max files per batch must be at most 100, got: " + maxFilesPerBatch);
            }
            if (maxConcurrentUploads > maxFilesPerBatch) {
                throw new IllegalArgumentException("Max concurrent uploads (" + maxConcurrentUploads + ") cannot exceed max files per batch (" + maxFilesPerBatch + ")");
            }
            if (enableBulkRateLimit && maxBulkUploadsPerHour <= 0) {
                throw new IllegalArgumentException("Max bulk uploads per hour must be positive when bulk rate limiting is enabled, got: " + maxBulkUploadsPerHour);
            }
        }
    }

    // Getters and setters
    public NameConflictPolicy getDefaultConflictPolicy() {
        return defaultConflictPolicy;
    }

    public void setDefaultConflictPolicy(NameConflictPolicy defaultConflictPolicy) {
        this.defaultConflictPolicy = defaultConflictPolicy;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getUploadTempDir() {
        return uploadTempDir;
    }

    public void setUploadTempDir(String uploadTempDir) {
        this.uploadTempDir = uploadTempDir;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Quota getQuota() {
        return quota;
    }

    public void setQuota(Quota quota) {
        this.quota = quota;
    }

    public VirusScanning getVirusScanning() {
        return virusScanning;
    }

    public void setVirusScanning(VirusScanning virusScanning) {
        this.virusScanning = virusScanning;
    }

    public AuditLogging getAuditLogging() {
        return auditLogging;
    }

    public void setAuditLogging(AuditLogging auditLogging) {
        this.auditLogging = auditLogging;
    }

    public Monitoring getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(Monitoring monitoring) {
        this.monitoring = monitoring;
    }

    public BulkUpload getBulkUpload() {
        return bulkUpload;
    }

    public void setBulkUpload(BulkUpload bulkUpload) {
        this.bulkUpload = bulkUpload;
    }

    // Métodos convenientes para compatibilidade
    
    /**
     * Método de conveniência para obter o tamanho máximo de arquivo.
     * 
     * <p>Este método fornece uma interface simplificada para acessar a configuração
     * de tamanho máximo de arquivo, que internamente é gerenciada pela configuração
     * de virus scanning. É fornecido para compatibilidade com versões anteriores
     * e para facilitar o acesso a esta configuração frequentemente utilizada.
     * 
     * <p><strong>Implementação</strong>: Delega para {@code virusScanning.getMaxFileSizeBytes()}
     * 
     * <p><strong>Valor padrão</strong>: 10485760 bytes (10 MB)
     * 
     * @return Tamanho máximo permitido para arquivos em bytes
     * @since 1.0.0
     */
    public Long getMaxFileSize() {
        return virusScanning.getMaxFileSizeBytes();
    }

    /**
     * Método de conveniência para obter lista de tipos de arquivo permitidos.
     * 
     * <p>Este método fornece uma lista simplificada de extensões de arquivo
     * comumente aceitas quando não há configuração específica de MIME types.
     * É fornecido para compatibilidade e para facilitar configurações básicas.
     * 
     * <p><strong>Comportamento</strong>:
     * <ul>
     *   <li>Se {@code security.allowedMimeTypes} estiver vazio: retorna extensões padrão seguras</li>
     *   <li>Se houver MIME types configurados: retorna lista vazia (usa a configuração MIME)</li>
     * </ul>
     * 
     * <p><strong>Extensões padrão retornadas</strong>:
     * {@code ["jpg", "jpeg", "png", "gif", "pdf", "txt", "csv"]}
     * 
     * <p><strong>Importante</strong>: Este método não reflete a configuração real
     * de segurança quando MIME types específicos estão configurados. Para
     * validação de segurança real, use {@code security.getAllowedMimeTypes()}.
     * 
     * @return Lista de extensões de arquivo permitidas ou lista vazia se MIME types estão configurados
     * @since 1.0.0
     */
    public List<String> getAllowedFileTypes() {
        List<String> types = new ArrayList<>();
        // Converte MIME types em extensões comuns para compatibilidade
        if (security.getAllowedMimeTypes().isEmpty()) {
            // Se não há tipos específicos, retorna tipos comuns seguros
            types.add("jpg");
            types.add("jpeg");
            types.add("png");
            types.add("gif");
            types.add("pdf");
            types.add("txt");
            types.add("csv");
        }
        return types;
    }

    /**
     * Valida todas as configurações após a inicialização
     */
    @PostConstruct
    public void validateConfiguration() {
        // Validar configurações básicas
        if (uploadDir == null || uploadDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Upload directory cannot be null or empty");
        }

        if (uploadTempDir == null || uploadTempDir.trim().isEmpty()) {
            uploadTempDir = System.getProperty("java.io.tmpdir");
        }

        if (defaultConflictPolicy == null) {
            defaultConflictPolicy = NameConflictPolicy.MAKE_UNIQUE;
        }

        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Max file size must be positive, got: " + maxFileSizeBytes);
        }

        // Validar configurações específicas
        if (virusScanning != null) {
            virusScanning.validate();
        }
        if (auditLogging != null) {
            auditLogging.validate();
        }
        if (monitoring != null) {
            monitoring.validate();
        }
        if (rateLimit != null) {
            rateLimit.validate();
        }
        if (quota != null) {
            quota.validate();
        }
        if (security != null) {
            security.validate();
        }
        if (bulkUpload != null) {
            bulkUpload.validate();
        }
    }
}

