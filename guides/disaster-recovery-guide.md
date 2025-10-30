# Disaster Recovery Guide

## Storage replication

- Use storage that supports cross-region replication or mirrored volumes.
- Schedule periodic snapshots of the upload directory or bucket.

## Metadata backups

- Persist metadata such as file paths, owners and checksums in a database.
- Back up the database regularly and provide scripts to restore files from metadata.

## Service fallback

- If ClamAV is unavailable, reject uploads or switch to a secondary scanning service.
- When primary storage is offline, buffer uploads locally and retry or redirect to a secondary bucket.
