# Security Hardening Guide

## Production defaults

Enable strict validation and antivirus scanning in production:

```properties
file.management.security.strict-validation=true
virus-scanning.enabled=true
```

## Transport security

- Use mTLS between clients and the file service to ensure mutual authentication.
- Require signed requests (for example, HMAC headers) for internal integrations.
- Rotate signing keys regularly and store them in a secure vault.
