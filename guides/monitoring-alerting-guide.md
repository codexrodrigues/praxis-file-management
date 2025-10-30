# Monitoring & Alerting Guide

## Metrics and logs

- Expose application metrics with Micrometer and scrape them via Prometheus.
- Emit structured JSON logs and ship them to ELK or another log stack.

## Alerts

- Trigger alerts when antivirus scans fail (`file_antivirus_failures_total` increases).
- Monitor `file_errors_total` and alert on abnormal spikes.
- Notify on storage connectivity errors or high latency.
