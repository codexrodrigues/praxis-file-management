# Performance Tuning Guide

## Rate limiting

- Adjust limits with `file.management.rate-limit.per-minute` and `file.management.rate-limit.per-hour` to match expected throughput.

## Bulk uploads

- Enable batch processing with `file.management.bulk-upload.enabled=true`.
- Tune `file.management.bulk-upload.max-files` for suitable batch size.

## Scalable storage

- Offload large payloads using pre-signed URLs.
  - Configure S3 or GCS credentials.
  - Use `/api/files/upload/presign` to obtain a temporary URL and upload directly from clients.
