# Status

Última atualização: 2026-03-15
Estado geral: backend e frontend já operam no contrato final sem legado; validação backend-only foi concluída; próximo foco é automação cross-repo e fechamento operacional
Responsável atual: análise assistida por Codex

## Situação atual

O `praxis-file-management` e o `praxis-files-upload` já passaram pelo corte do contrato final.

Shape final atual do web layer:
- sucesso: `{ status, message, data, timestamp }`
- erro: `{ status, message, errors, timestamp, traceId }`
- bulk: resultados especializados em `data.results`
- exceções deliberadas: `presign`, `health`, `metrics`

## Status por macrotema

| Tema | Status | Observação |
|---|---|---|
| Diagnóstico dos starters Java Praxis | Concluído | Fluxo e intenção entendidos |
| Diagnóstico do `praxis-file-management` | Concluído | Desalinhamentos principais mapeados |
| Diagnóstico do `praxis-files-upload` | Concluído | Lacunas reais de integração identificadas |
| Plano por fases | Concluído | Registrado em `EXECUTION_PLAN.md` |
| Matriz de compatibilidade | Atualizada | Agora descreve a incompatibilidade real pós-corte do backend |
| Backlog operacional | Atualizado | Próximo item operacional é automação de validação |
| Alias `/api/files/bulk` | Concluído | Mantido no backend |
| `metadata` e `conflictPolicy` no upload simples | Concluído | Processados no controller |
| Arrays homogêneos no request de bulk | Concluído com limite | Arrays heterogêneos seguem retornando `400` |
| Envelope final do backend | Concluído | `ApiEnvelopeFactory` centraliza sucesso |
| Contrato final de erro | Concluído | `ErrorResponse` centraliza `errors[]` |
| Endpoint auxiliar alinhado | Concluído | `MonitoringController#getVersion` usa o mesmo shape |
| OpenAPI e README alinhados ao runtime principal | Concluído | exemplos críticos revisados |
| Semântica de bulk parcial | Concluído com exceção arquitetural | `207` agora expõe `status: partial_success` no corpo, fora do canônico Praxis atual |
| Documentação histórica separada da ativa | Concluído | arquivos transitórios movidos para `history/` |
| Endurecimento de testes de contrato do backend | Concluído | suíte focalizada agora protege melhor o shape final antes da etapa Angular |
| Limpeza do `praxis-files-upload` | Concluído | tipos, serviços, docs e exemplos ficaram sem fallback legado |
| Validação backend-only por HTTP | Concluído | `config`, `upload`, `bulk`, `quota` e `rate limit` exercitados sem Angular/Playwright |
| Restrições ad hoc de tipo | Concluído | `allowedExtensions` e `acceptMimeTypes` corrigidos no core |
| Rate limit HTTP 429 padronizado | Concluído | filtro agora responde envelope final com `X-RateLimit-*` |

## Próximos passos recomendados

1. Usar `ENVELOPE_ALIGNMENT.md` como referência principal do contrato final do backend.
2. Usar `COMPATIBILITY_MATRIX.md` como histórico das adaptações que já foram necessárias no Angular.
3. Considerar estabilizados no backend:
   - alias de bulk;
   - suporte de `metadata` e `conflictPolicy` no upload simples;
   - arrays homogêneos no request de bulk;
   - envelope final de sucesso e erro;
   - documentação principal alinhada ao runtime.
4. Tratar a limitação de opções por arquivo no bulk como decisão aceita nesta fase.
5. Criar validação automatizada cross-repo para reduzir risco de handoff entre backend e Angular.
6. Consolidar um smoke backend-only reproduzível em script.
7. Só depois abrir discussão sobre convergência do `FieldControlType.FILE_UPLOAD`.
8. Decidir no nível da plataforma se `partial_success` vira extensão oficial ou exceção permanente do módulo.

## Checkpoint de retomada

Se houver crash ou interrupção, retomar nesta ordem:

1. Ler `STATUS.md`.
2. Ler `EXECUTION_PLAN.md`.
3. Ler `ENVELOPE_ALIGNMENT.md`.
4. Ler `COMPATIBILITY_MATRIX.md`.
5. Ler `OPERATIONAL_BACKLOG.md`.
6. Consultar `CONVERSATION_LOG.md` para recuperar o histórico das decisões.
7. Retomar a partir do próximo item pendente:
   - automação de validação do contrato final e fechamento documental.

## Decisões já tomadas

- Não manter legado no backend antes de entrar em produção.
- Não manter legado no frontend antes de entrar em produção.
- Não reabrir refatoração de core de bulk por item agora.
- Não voltar a compatibilizar o backend para o shape antigo do Angular.
- Tratar backend e frontend como duas partes do mesmo contrato.
- Manter `presign`, `health` e `metrics` fora do envelope principal.

## Entregas concluídas no backend

1. Documentação persistente de retomada criada em `docs/praxis-alignment/`.
2. Alias backend adicionado para `POST /api/files/bulk`.
3. Upload simples atualizado para aceitar `metadata` e `conflictPolicy`.
4. Bulk request atualizado para aceitar `metadata` e `conflictPolicy` homogêneos.
5. Envelope final implementado em `ApiEnvelopeFactory`.
6. `ConfigController`, `FileController` e `MonitoringController#getVersion` migrados para o shape final.
7. `GlobalExceptionHandler` e `ErrorResponse` migrados para o contrato final de erro.
8. README, OpenAPI e documentos principais revisados para remover drift crítico.
9. `praxis-files-upload` adaptado ao contrato final sem fallback legado em código, docs e exemplos.
10. `LocalStorageFileService` corrigido para aplicar restrições ad hoc de tipo sem bypass lógico.
11. `RateLimitingFilter` corrigido para responder `429` padronizado no próprio filtro.

## Validação local registrada

Comandos executados com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=MonitoringControllerTest,GlobalExceptionHandlerTest,ConfigControllerTest,FileControllerBulkUploadTest,FileControllerMimeTypeTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

/opt/maven/bin/mvn -f pom.xml -pl praxis-files-core -Dtest=FileUploadApiTest test

/opt/maven/bin/mvn -f pom.xml -pl praxis-files-web \
  -Dtest=br.com.praxis.filemanagement.web.filter.RateLimitingFilterTest test
```

Observações:
- warnings de ambiente (`JAVA_HOME`, byte-buddy dinâmico) não bloquearam a execução;
- a suíte cobre envelope final de sucesso, erro e bulk no backend web;
- o consumidor Angular já foi limpo para o contrato final;
- o smoke backend-only confirmou:
  - `GET /api/files/config` = `200`
  - upload simples inválido por `allowedExtensions` = `400`
  - upload simples inválido por `acceptMimeTypes` = `400`
  - bulk parcial por restrição de tipo = `207`
  - rate limit excedido = `429` com envelope final
- ainda falta teste de contrato automatizado cross-repo com `praxis-files-upload`.

## Decisão de escopo aceita

- O suporte real a opções distintas por arquivo no bulk continua fora do escopo atual.
- A próxima fase começa em automação e governança do contrato, não em nova compatibilização manual.
