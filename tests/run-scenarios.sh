#!/usr/bin/env bash
# Runs the file-management test matrix against a local server.
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
TMP_DIR="$(mktemp -d)"

setup_files() {
  echo "preparing sample files in $TMP_DIR"
  echo 'hello' > "$TMP_DIR/sample.txt"
  : > "$TMP_DIR/empty.txt"
  dd if=/dev/zero bs=1M count=11 of="$TMP_DIR/large.bin" >/dev/null 2>&1
  printf 'X5O!P%%@@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*' > "$TMP_DIR/eicar.txt"
}

cleanup() {
  rm -rf "$TMP_DIR"
}

expect_status() {
  local expected="$1"; shift
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$@")
  if [[ "$code" != "$expected" ]]; then
    echo "Expected $expected got $code"
    exit 1
  fi
}

happy_path() {
  echo "Happy path"
  expect_status 201 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files"
}

empty_file() {
  echo "Empty file"
  expect_status 400 -F file=@"$TMP_DIR/empty.txt" "$BASE_URL/api/files"
}

too_large() {
  echo "413 file too large"
  expect_status 413 -F file=@"$TMP_DIR/large.bin" "$BASE_URL/api/files"
}

unsupported_media() {
  echo "415 unsupported media"
  expect_status 415 -H "Content-Type: multipart/form-data" -F "file=@$TMP_DIR/sample.txt;type=application/x-msdownload" "$BASE_URL/api/files"
}

rate_limit() {
  echo "429 rate limit"
  for i in {1..5}; do curl -s -o /dev/null "$BASE_URL/api/files" -F file=@"$TMP_DIR/sample.txt" & done
  wait
  expect_status 429 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files"
}

quota_exceeded() {
  echo "429 quota exceeded"
  expect_status 201 -H "X-Tenant-Id: tenant" -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files"
  expect_status 429 -H "X-Tenant-Id: tenant" -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files"
}

antivirus() {
  echo "Antivirus"
  expect_status 400 -F file=@"$TMP_DIR/eicar.txt" "$BASE_URL/api/files"
}

duplicate_name_reject() {
  echo "Duplicate name REJECT"
  expect_status 201 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files?conflict=REJECT"
  expect_status 409 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files?conflict=REJECT"
}

duplicate_name_rename() {
  echo "Duplicate name RENAME"
  expect_status 201 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files?conflict=RENAME"
  expect_status 201 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files?conflict=RENAME"
}

duplicate_name_overwrite() {
  echo "Duplicate name OVERWRITE"
  expect_status 201 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files"
  expect_status 200 -F file=@"$TMP_DIR/sample.txt" "$BASE_URL/api/files?conflict=OVERWRITE"
}

path_traversal() {
  echo "Path traversal"
  expect_status 400 -F "file=@$TMP_DIR/sample.txt;filename=../evil.txt" "$BASE_URL/api/files"
}

web_off() {
  echo "Web disabled"
  expect_status 404 "$BASE_URL/api/files"
}

monitoring() {
  echo "Monitoring"
  expect_status 403 "$BASE_URL/file-management/monitoring/health"
}

setup_files
trap cleanup EXIT

happy_path
empty_file
too_large
unsupported_media
rate_limit
quota_exceeded
antivirus
duplicate_name_reject
duplicate_name_rename
duplicate_name_overwrite
path_traversal
web_off
monitoring

echo "All scenarios passed"
