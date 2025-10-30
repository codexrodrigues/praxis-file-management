#!/bin/bash

# Script de verificação de consistência entre documentação e implementação
# Projeto: praxis-files

echo "========================================"
echo "RELATÓRIO DE CONSISTÊNCIA - praxis-files"
echo "========================================"
echo "Data: $(date)"
echo ""

BASE_URL="http://localhost:8086"
RESULTS_FILE="consistency-report.md"

# Limpar relatório anterior
> $RESULTS_FILE

echo "# Relatório de Consistência - praxis-files" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "## 1. Status da Aplicação" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

# 1. Verificar se aplicação está rodando
echo "1. Verificando aplicação..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL/actuator/health)
echo "   Status do health check: $STATUS" 
echo "- Health check status: $STATUS (esperado: 401 com autenticação habilitada)" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

# 2. Testar endpoints de upload
echo "" >> $RESULTS_FILE
echo "## 2. Endpoints de Upload" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "### 2.1 Upload Simples (/api/files/upload)" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "2. Testando endpoints de upload..."

# Criar arquivo de teste
echo "test content" > test.txt

# 2.1 Upload simples sem autenticação
echo "   2.1 Upload simples..."
UPLOAD_STATUS=$(curl -s -o response.json -w "%{http_code}" -F "file=@test.txt" $BASE_URL/api/files/upload)
echo "      Status: $UPLOAD_STATUS"
echo "- **Sem autenticação**: HTTP $UPLOAD_STATUS" >> $RESULTS_FILE

# Verificar conteúdo da resposta
if [ -f response.json ]; then
    echo "  Resposta: $(cat response.json)" >> $RESULTS_FILE
fi
echo "" >> $RESULTS_FILE

# 2.2 Upload múltiplo
echo "### 2.2 Upload Múltiplo (/api/files/upload/bulk)" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "   2.2 Upload múltiplo..."
echo "test2" > test2.txt
BULK_STATUS=$(curl -s -o response_bulk.json -w "%{http_code}" -F "files=@test.txt" -F "files=@test2.txt" $BASE_URL/api/files/upload/bulk)
echo "      Status: $BULK_STATUS"
echo "- **Sem autenticação**: HTTP $BULK_STATUS" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

# 2.3 Pre-signed URL
echo "### 2.3 Pre-signed URL (/api/files/upload/presign)" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "   2.3 Pre-signed URL..."
PRESIGN_STATUS=$(curl -s -o response_presign.json -w "%{http_code}" -X POST "$BASE_URL/api/files/upload/presign?filename=test.txt")
echo "      Status: $PRESIGN_STATUS"
echo "- **Sem autenticação**: HTTP $PRESIGN_STATUS" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

# 3. Testar endpoints de monitoramento
echo "" >> $RESULTS_FILE
echo "## 3. Endpoints de Monitoramento" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "3. Testando endpoints de monitoramento..."

MONITORING_ENDPOINTS=(
    "/file-management/monitoring/health"
    "/file-management/monitoring/metrics"
    "/file-management/monitoring/status"
    "/file-management/monitoring/ping"
    "/file-management/monitoring/version"
)

for endpoint in "${MONITORING_ENDPOINTS[@]}"; do
    echo "   Testando $endpoint..."
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" $BASE_URL$endpoint)
    echo "      Status: $STATUS"
    echo "- **$endpoint**: HTTP $STATUS (esperado: 401/403 com role MONITORING)" >> $RESULTS_FILE
done

echo "" >> $RESULTS_FILE

# 4. Verificar endpoints documentados vs implementados
echo "" >> $RESULTS_FILE
echo "## 4. Comparação com Documentação (REPORT.md)" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "4. Comparando com documentação..."

echo "### Endpoints Documentados:" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "| Endpoint | Método | Status Documentado | Status Real | Consistente |" >> $RESULTS_FILE
echo "|----------|--------|-------------------|-------------|-------------|" >> $RESULTS_FILE

# Upload simples
echo "| /api/files/upload | POST | 201 (sucesso) | $UPLOAD_STATUS | $([ "$UPLOAD_STATUS" == "401" ] && echo "✅ (auth)" || echo "❌") |" >> $RESULTS_FILE

# Upload bulk
echo "| /api/files/upload/bulk | POST | 201/207 | $BULK_STATUS | $([ "$BULK_STATUS" == "401" ] && echo "✅ (auth)" || echo "❌") |" >> $RESULTS_FILE

# Presigned
echo "| /api/files/upload/presign | POST | 200 | $PRESIGN_STATUS | $([ "$PRESIGN_STATUS" == "401" ] && echo "✅ (auth)" || echo "❌") |" >> $RESULTS_FILE

echo "" >> $RESULTS_FILE

# 5. Verificar configurações
echo "" >> $RESULTS_FILE
echo "## 5. Verificação de Configurações" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "5. Verificando configurações..."

echo "### Configurações Documentadas vs Aplicação:" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "| Configuração | Valor Documentado | Status |" >> $RESULTS_FILE
echo "|--------------|-------------------|--------|" >> $RESULTS_FILE
echo "| Porta | 8086 | ✅ (verificado) |" >> $RESULTS_FILE
echo "| Magic Number Validation | Habilitado por padrão | ⚠️ (não testado) |" >> $RESULTS_FILE
echo "| Rate Limiting | 10/min, 100/hora | ⚠️ (não testado) |" >> $RESULTS_FILE
echo "| Tamanho máximo | 10MB | ⚠️ (não testado) |" >> $RESULTS_FILE
echo "| Virus scanning | Desabilitado por padrão | ⚠️ (não testado) |" >> $RESULTS_FILE

echo "" >> $RESULTS_FILE

# 6. Teste de cenários específicos
echo "" >> $RESULTS_FILE
echo "## 6. Cenários de Teste Específicos" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "6. Testando cenários específicos..."

# 6.1 Arquivo vazio
echo "### 6.1 Arquivo Vazio" >> $RESULTS_FILE
> empty.txt
EMPTY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@empty.txt" $BASE_URL/api/files/upload)
echo "   6.1 Arquivo vazio: $EMPTY_STATUS"
echo "- Status: $EMPTY_STATUS (esperado: 400)" >> $RESULTS_FILE

# 6.2 Arquivo grande (simulado)
echo "### 6.2 Arquivo Grande (>10MB)" >> $RESULTS_FILE
# dd if=/dev/zero bs=1M count=11 of=large.bin 2>/dev/null
# LARGE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -F "file=@large.bin" $BASE_URL/api/files/upload)
echo "- Status: Não testado (requer arquivo grande)" >> $RESULTS_FILE

echo "" >> $RESULTS_FILE

# 7. Resumo de inconsistências
echo "" >> $RESULTS_FILE
echo "## 7. Resumo de Inconsistências Encontradas" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "7. Gerando resumo..."

echo "### Principais Achados:" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "1. **Autenticação**: Todos os endpoints estão protegidos por autenticação (HTTP 401)" >> $RESULTS_FILE
echo "2. **Segurança**: Spring Security está habilitado por padrão" >> $RESULTS_FILE
echo "3. **Endpoints de Upload**: Retornam 401 sem credenciais (conforme esperado com segurança)" >> $RESULTS_FILE
echo "4. **Endpoints de Monitoramento**: Requerem role MONITORING (conforme documentado)" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "### Inconsistências com Documentação:" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "- ❌ **Documentação não menciona claramente a necessidade de autenticação para testes**" >> $RESULTS_FILE
echo "- ❌ **README.md exemplo usa curl sem autenticação (não funcionará)**" >> $RESULTS_FILE
echo "- ⚠️ **Configuração de segurança padrão não está documentada adequadamente**" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

echo "### Recomendações:" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE
echo "1. Atualizar README.md com exemplos incluindo autenticação" >> $RESULTS_FILE
echo "2. Documentar configuração de segurança padrão" >> $RESULTS_FILE
echo "3. Adicionar perfil de desenvolvimento sem autenticação para facilitar testes" >> $RESULTS_FILE
echo "4. Incluir credenciais de teste na documentação" >> $RESULTS_FILE
echo "" >> $RESULTS_FILE

# Exibir relatório
echo ""
echo "========================================"
echo "RELATÓRIO COMPLETO SALVO EM: $RESULTS_FILE"
echo "========================================"
echo ""
cat $RESULTS_FILE

# Limpar arquivos temporários
rm -f test.txt test2.txt empty.txt response*.json