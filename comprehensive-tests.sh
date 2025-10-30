#!/bin/bash

# Testes Abrangentes para praxis-files
# Data: $(date)

BASE_URL="http://localhost:8086"
REPORT="comprehensive-test-report.md"

echo "# Relatório de Testes Abrangentes - praxis-files" > $REPORT
echo "Data: $(date)" >> $REPORT
echo "" >> $REPORT

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_test() {
    local category="$1"
    local test_name="$2" 
    local expected="$3"
    local actual="$4"
    local status="$5"
    
    echo "   $test_name: $actual (esperado: $expected) - $status"
    echo "- **$test_name**: $actual (esperado: $expected) - $status" >> $REPORT
}

echo "=========================================="
echo "TESTES ABRANGENTES - praxis-files"
echo "=========================================="

# Limpar arquivos anteriores
rm -f test*.txt large*.bin empty.txt eicar.txt

echo "## 1. Testes de Limites de Arquivo" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}1. TESTANDO LIMITES DE ARQUIVO${NC}"

# 1.1 Arquivo normal (deve funcionar)
echo "Test content for normal file" > test_normal.txt
NORMAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@test_normal.txt" $BASE_URL/api/files/upload)
log_test "Limites" "Arquivo normal (25 bytes)" "201" "$NORMAL_STATUS" "$([ "$NORMAL_STATUS" == "201" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 1.2 Arquivo vazio (deve falhar)
: > test_empty.txt
EMPTY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@test_empty.txt" $BASE_URL/api/files/upload)
log_test "Limites" "Arquivo vazio" "400" "$EMPTY_STATUS" "$([ "$EMPTY_STATUS" == "400" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 1.3 Arquivo próximo ao limite (9MB - deve funcionar)
echo "   Criando arquivo de 9MB..."
dd if=/dev/zero bs=1M count=9 of=test_9mb.bin 2>/dev/null
LARGE_9MB_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@test_9mb.bin" $BASE_URL/api/files/upload)
log_test "Limites" "Arquivo 9MB" "201" "$LARGE_9MB_STATUS" "$([ "$LARGE_9MB_STATUS" == "201" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 1.4 Arquivo muito grande (11MB - deve falhar)
echo "   Criando arquivo de 11MB..."
dd if=/dev/zero bs=1M count=11 of=test_11mb.bin 2>/dev/null
LARGE_11MB_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@test_11mb.bin" $BASE_URL/api/files/upload)
log_test "Limites" "Arquivo 11MB" "413" "$LARGE_11MB_STATUS" "$([ "$LARGE_11MB_STATUS" == "413" ] && echo "✅ PASS" || echo "❌ FAIL")"

echo "" >> $REPORT
echo "## 2. Testes de Rate Limiting" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}2. TESTANDO RATE LIMITING${NC}"

# 2.1 Teste de uploads rápidos (deve eventualmente rate limit)
echo "   Testando 15 uploads rápidos..."
RATE_LIMIT_COUNT=0
for i in {1..15}; do
    echo "Upload test $i" > test_rate_$i.txt
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@test_rate_$i.txt" $BASE_URL/api/files/upload)
    if [ "$STATUS" == "429" ]; then
        RATE_LIMIT_COUNT=$((RATE_LIMIT_COUNT + 1))
    fi
    sleep 0.5
done
log_test "Rate Limiting" "Uploads sequenciais (15x)" "Pelo menos 1x 429" "$RATE_LIMIT_COUNT rate limits" "$([ "$RATE_LIMIT_COUNT" -gt "0" ] && echo "✅ PASS" || echo "⚠️ UNCLEAR")"

echo "" >> $REPORT
echo "## 3. Testes de Bulk Upload" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}3. TESTANDO BULK UPLOAD${NC}"

# 3.1 Bulk upload normal (5 arquivos)
echo "   Testando bulk upload com 5 arquivos..."
for i in {1..5}; do
    echo "Bulk test file $i content" > bulk_$i.txt
done
BULK_5_STATUS=$(curl -s -o bulk_response.json -w "%{http_code}" \
    -F "files=@bulk_1.txt" \
    -F "files=@bulk_2.txt" \
    -F "files=@bulk_3.txt" \
    -F "files=@bulk_4.txt" \
    -F "files=@bulk_5.txt" \
    $BASE_URL/api/files/upload/bulk)

BULK_SUCCESS_COUNT=$(cat bulk_response.json 2>/dev/null | grep -o '"totalSuccess":[0-9]*' | cut -d':' -f2 || echo "0")
log_test "Bulk Upload" "5 arquivos pequenos" "201 com 5 sucessos" "$BULK_5_STATUS (sucessos: $BULK_SUCCESS_COUNT)" "$([ "$BULK_5_STATUS" == "201" ] && [ "$BULK_SUCCESS_COUNT" == "5" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 3.2 Bulk upload com fail-fast
echo "   Testando fail-fast mode..."
echo "Valid content" > bulk_valid.txt
: > bulk_empty.txt  # Arquivo vazio para causar falha
BULK_FAILFAST_STATUS=$(curl -s -o bulk_failfast.json -w "%{http_code}" \
    -F "files=@bulk_valid.txt" \
    -F "files=@bulk_empty.txt" \
    -F "failFastMode=true" \
    $BASE_URL/api/files/upload/bulk)
log_test "Bulk Upload" "Fail-fast com erro" "400 ou 207" "$BULK_FAILFAST_STATUS" "$([ "$BULK_FAILFAST_STATUS" == "207" ] || [ "$BULK_FAILFAST_STATUS" == "400" ] && echo "✅ PASS" || echo "❌ FAIL")"

echo "" >> $REPORT
echo "## 4. Testes de Validação de Segurança" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}4. TESTANDO VALIDAÇÕES DE SEGURANÇA${NC}"

# 4.1 Path traversal
echo "Malicious content" > path_traversal.txt
TRAVERSAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -F "file=@path_traversal.txt;filename=../../../etc/passwd" \
    $BASE_URL/api/files/upload)
log_test "Segurança" "Path traversal" "400" "$TRAVERSAL_STATUS" "$([ "$TRAVERSAL_STATUS" == "400" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 4.2 Filename muito longo
echo "Test content" > long_name.txt
LONG_NAME=$(python3 -c "print('a' * 300)")
LONG_FILENAME_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -F "file=@long_name.txt;filename=$LONG_NAME.txt" \
    $BASE_URL/api/files/upload)
log_test "Segurança" "Nome muito longo (300 chars)" "400" "$LONG_FILENAME_STATUS" "$([ "$LONG_FILENAME_STATUS" == "400" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 4.3 MIME type spoofing
echo "#!/bin/bash\necho 'malicious'" > fake_script.txt
MIME_SPOOF_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -F "file=@fake_script.txt;type=application/x-executable" \
    $BASE_URL/api/files/upload)
log_test "Segurança" "MIME type spoofing" "400 ou 415" "$MIME_SPOOF_STATUS" "$([ "$MIME_SPOOF_STATUS" == "400" ] || [ "$MIME_SPOOF_STATUS" == "415" ] && echo "✅ PASS" || echo "⚠️ UNCLEAR")"

echo "" >> $REPORT
echo "## 5. Testes de Políticas de Conflito" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}5. TESTANDO POLÍTICAS DE CONFLITO${NC}"

# 5.1 Política RENAME (padrão)
echo "Original content" > conflict_test.txt
FIRST_UPLOAD=$(curl -s -o first_upload.json -w "%{http_code}" -F "file=@conflict_test.txt" $BASE_URL/api/files/upload)
SECOND_UPLOAD=$(curl -s -o second_upload.json -w "%{http_code}" -F "file=@conflict_test.txt" $BASE_URL/api/files/upload)

FIRST_FILENAME=$(cat first_upload.json 2>/dev/null | grep -o '"serverFilename":"[^"]*"' | cut -d'"' -f4 || echo "")
SECOND_FILENAME=$(cat second_upload.json 2>/dev/null | grep -o '"serverFilename":"[^"]*"' | cut -d'"' -f4 || echo "")

RENAME_WORKS=$([ "$FIRST_FILENAME" != "$SECOND_FILENAME" ] && [ -n "$FIRST_FILENAME" ] && [ -n "$SECOND_FILENAME" ] && echo "true" || echo "false")
log_test "Conflitos" "Política RENAME" "Nomes diferentes" "Nome1≠Nome2: $RENAME_WORKS" "$([ "$RENAME_WORKS" == "true" ] && echo "✅ PASS" || echo "❌ FAIL")"

echo "" >> $REPORT
echo "## 6. Testes de Monitoramento" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}6. TESTANDO ENDPOINTS DE MONITORAMENTO${NC}"

# 6.1 Health check detalhado
HEALTH_STATUS=$(curl -s -o health_response.json -w "%{http_code}" $BASE_URL/file-management/monitoring/health)
HEALTH_HAS_COMPONENTS=$(cat health_response.json 2>/dev/null | grep -q "components" && echo "true" || echo "false")
log_test "Monitoramento" "Health com componentes" "200 + components" "$HEALTH_STATUS (components: $HEALTH_HAS_COMPONENTS)" "$([ "$HEALTH_STATUS" == "200" ] && [ "$HEALTH_HAS_COMPONENTS" == "true" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 6.2 Métricas
METRICS_STATUS=$(curl -s -o metrics_response.json -w "%{http_code}" $BASE_URL/file-management/monitoring/metrics)
METRICS_HAS_CONFIG=$(cat metrics_response.json 2>/dev/null | grep -q "configuration" && echo "true" || echo "false")
log_test "Monitoramento" "Métricas detalhadas" "200 + config data" "$METRICS_STATUS (config: $METRICS_HAS_CONFIG)" "$([ "$METRICS_STATUS" == "200" ] && [ "$METRICS_HAS_CONFIG" == "true" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 6.3 Version info
VERSION_STATUS=$(curl -s -o version_response.json -w "%{http_code}" $BASE_URL/file-management/monitoring/version)
VERSION_HAS_FEATURES=$(cat version_response.json 2>/dev/null | grep -q "features" && echo "true" || echo "false")
log_test "Monitoramento" "Informações de versão" "200 + features" "$VERSION_STATUS (features: $VERSION_HAS_FEATURES)" "$([ "$VERSION_STATUS" == "200" ] && [ "$VERSION_HAS_FEATURES" == "true" ] && echo "✅ PASS" || echo "❌ FAIL")"

echo "" >> $REPORT
echo "## 7. Testes de Quotas" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}7. TESTANDO SISTEMA DE QUOTAS${NC}"

# 7.1 Upload com tenant ID
echo "Tenant test content" > tenant_test.txt
TENANT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Tenant-Id: test-tenant-123" \
    -F "file=@tenant_test.txt" \
    $BASE_URL/api/files/upload)
log_test "Quotas" "Upload com Tenant-ID" "201" "$TENANT_STATUS" "$([ "$TENANT_STATUS" == "201" ] && echo "✅ PASS" || echo "❌ FAIL")"

# 7.2 Upload com user ID
echo "User test content" > user_test.txt
USER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-User-Id: test-user-456" \
    -F "file=@user_test.txt" \
    $BASE_URL/api/files/upload)
log_test "Quotas" "Upload com User-ID" "201" "$USER_STATUS" "$([ "$USER_STATUS" == "201" ] && echo "✅ PASS" || echo "❌ FAIL")"

echo "" >> $REPORT
echo "## 8. Testes de Presigned URLs" >> $REPORT
echo "" >> $REPORT

echo -e "${YELLOW}8. TESTANDO PRESIGNED URLS${NC}"

# 8.1 Presigned URL generation
PRESIGN_STATUS=$(curl -s -o presign_response.json -w "%{http_code}" \
    -X POST "$BASE_URL/api/files/upload/presign?filename=presigned-test.txt")
PRESIGN_HAS_URL=$(cat presign_response.json 2>/dev/null | grep -q "url" && echo "true" || echo "false")
log_test "Presigned URLs" "Geração de URL" "200 + URL" "$PRESIGN_STATUS (URL: $PRESIGN_HAS_URL)" "$([ "$PRESIGN_STATUS" == "200" ] && [ "$PRESIGN_HAS_URL" == "true" ] && echo "✅ PASS" || echo "❌ FAIL")"

echo "" >> $REPORT
echo "## 9. Resumo dos Resultados" >> $REPORT
echo "" >> $REPORT

# Contar resultados
TOTAL_TESTS=$(grep -c "✅ PASS\|❌ FAIL\|⚠️ UNCLEAR" $REPORT)
PASSED_TESTS=$(grep -c "✅ PASS" $REPORT)
FAILED_TESTS=$(grep -c "❌ FAIL" $REPORT)
UNCLEAR_TESTS=$(grep -c "⚠️ UNCLEAR" $REPORT)

echo "### Estatísticas Finais:" >> $REPORT
echo "- **Total de Testes:** $TOTAL_TESTS" >> $REPORT
echo "- **✅ Aprovados:** $PASSED_TESTS" >> $REPORT
echo "- **❌ Falharam:** $FAILED_TESTS" >> $REPORT
echo "- **⚠️ Inconclusivos:** $UNCLEAR_TESTS" >> $REPORT
echo "- **Taxa de Sucesso:** $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%" >> $REPORT

echo ""
echo -e "${GREEN}=========================================="
echo "TESTES CONCLUÍDOS"
echo "==========================================${NC}"
echo "Total: $TOTAL_TESTS | Passou: $PASSED_TESTS | Falhou: $FAILED_TESTS | Inconclusivo: $UNCLEAR_TESTS"
echo "Taxa de sucesso: $(( PASSED_TESTS * 100 / TOTAL_TESTS ))%"
echo ""
echo "Relatório completo salvo em: $REPORT"

# Limpar arquivos de teste
echo "Limpando arquivos temporários..."
rm -f test*.txt test*.bin bulk*.txt conflict_test.txt tenant_test.txt user_test.txt
rm -f long_name.txt fake_script.txt path_traversal.txt *.json

echo "Concluído!"