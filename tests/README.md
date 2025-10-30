# File Management Test Matrix

This directory provides curl scripts and HTTP request definitions that mirror the scenarios from the file-management test matrix.

## Requirements
* Running instance of the sample application on `http://localhost:8080`
* `curl` and `bash`

## Usage
Execute `run-scenarios.sh` to exercise all scenarios sequentially:

```bash
./run-scenarios.sh
```

Individual requests are available inside `matrix.http` for use with REST clients such as VS Code or IntelliJ.

Each section is labelled with the scenario it targets:

* Happy path upload
* Empty file
* 413 – file too large
* 415 – unsupported media type
* 429 – rate limit exceeded
* Antivirus detection
* Duplicate name conflict
* Path traversal attempt
* Web layer disabled
* Monitoring endpoint
