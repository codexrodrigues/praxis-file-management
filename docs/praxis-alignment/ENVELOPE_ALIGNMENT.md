# Envelope Alignment

## Objetivo

Registrar o shape final atual do web layer de `praxis-file-management`, as diferenças remanescentes em relação ao padrão da plataforma Praxis (`RestApiResponse`) e os limites deliberados antes da adaptação do frontend.

## Padrão Praxis de referência

Origem:
- `praxis-metadata-starter/src/main/java/org/praxisplatform/uischema/rest/response/RestApiResponse.java`

Shape padrão:

```json
{
  "status": "success|failure",
  "message": "Requisição realizada com sucesso",
  "data": {},
  "links": {},
  "errors": [],
  "timestamp": "2026-03-14T10:00:00"
}
```

Características:
- usa `status`, não `success`;
- tem `message` em sucesso e falha;
- centraliza erros em `errors`;
- suporta `links`;
- mantém `data` como payload principal.

## Estado atual no file management

Situação após o corte principal do backend:
- `ApiEnvelopeFactory` centraliza o envelope final do módulo web;
- `upload`, `bulk`, `config` e `monitoring/version` já usam `status`, `message`, `data` e `timestamp`;
- erros globais usam `status`, `message`, `errors` e `traceId`;
- campos legados de raiz deixaram de ser o contrato runtime;
- o frontend Angular ainda não foi adaptado a esse shape novo.

### Upload simples

Origem principal:
- `praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/controller/FileController.java`
- `praxis-files-core/src/main/java/br/com/praxis/filemanagement/core/utils/ErrorMessageUtils.java`

Shape atual:
- usa `status`, `message`, `data`, `timestamp`;
- falhas usam `status`, `message`, `errors`, `timestamp`, `traceId`;
- não usa `links`.

### Upload em lote

Origem principal:
- `praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/controller/FileController.java`

Shape atual:
- usa `status`, `message`, `data`, `timestamp`;
- resultados especializados do lote ficam em `data.results`;
- estatísticas do lote ficam em `data.totalProcessed`, `data.totalSuccess`, `data.totalFailed` e correlatos;
- não usa `links`.

### Config

Origem principal:
- `praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/controller/ConfigController.java`

Shape atual:
- `{ status, message, timestamp, data }`;
- sem `links`.

### Erros globais

Origem principal:
- `praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/error/GlobalExceptionHandler.java`
- `praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/error/ErrorResponse.java`

Shape atual:

```json
{
  "status": "failure",
  "message": "Formato JSON inválido para opções de upload",
  "errors": [
    {
      "code": "OPCOES_JSON_INVALIDAS",
      "message": "Formato JSON inválido para opções de upload",
      "details": "Verifique a sintaxe..."
    }
  ],
  "timestamp": "2026-03-14T10:00:00Z",
  "traceId": "..."
}
```

Características:
- erro fica centrado em `errors[]`;
- sucesso e falha já compartilham o mesmo eixo semântico;
- formato está mais próximo do modelo Praxis, embora ainda sem `links`.

## Divergências concretas

1. Links
- Praxis: `links`
- File management: ausente

2. Unificação do envelope
- Praxis: sucesso e falha compartilham o mesmo wrapper
- File management: convergência forte no web layer já aconteceu, mas ainda há endpoints especiais por função

3. Endpoints de contrato deliberadamente especial
- `POST /api/files/upload/presign` mantém shape raw de transporte (`uploadUrl`, `headers`, `fields`)
- endpoints de health/metrics monitoram payload operacional próprio e não foram envelopados nesta etapa

4. Semântica de sucesso parcial no bulk
- Praxis de referência: `status` documentado como `success|failure`
- File management: `POST /api/files/upload/bulk` e `POST /api/files/bulk` usam `status: "partial_success"` quando o HTTP é `207 Multi-Status`
- decisão atual: tratar isso como exceção arquitetural explícita do módulo web, motivada por semântica operacional, observabilidade e UX
- impacto: o módulo fica pragmaticamente mais honesto para operação enterprise, mas se afasta do shape canônico Praxis e deve ser tratado como exceção documentada até decisão de convergência da plataforma

## Leitura arquitetural

O problema principal agora não é mais legado no backend principal. O que resta é:
- alinhar consumidores do frontend ao contrato novo;
- decidir se haverá `links` em algum momento;
- decidir quanto aproximar o módulo de `RestApiResponse` além da semântica já adotada.

Isso impede que ele pareça um módulo Praxis nativo, mesmo estando funcionalmente integrado.

## Próximo passo técnico correto

1. Consolidar e documentar o shape final do web layer.
2. Adaptar o `praxis-files-upload` ao contrato novo.
3. Só depois discutir aproximação adicional com `RestApiResponse`, por exemplo `links`.

## Recomendação operacional

O próximo passo técnico deve ser:
- alinhar o frontend e consumidores ao shape novo;
- manter `ErrorResponse` como tipo público final do módulo web;
- só depois abrir a discussão de `links`;
- decidir em nível de plataforma se `partial_success` pode virar extensão oficial do padrão Praxis ou se o módulo deverá voltar a `success` com semântica parcial sustentada por campos auxiliares.

## Shape final atual

### Sucesso padrão

```json
{
  "status": "success",
  "message": "Mensagem de sucesso",
  "timestamp": "2026-03-14T10:00:00Z",
  "data": {}
}
```

### Erro padrão

```json
{
  "status": "failure",
  "message": "Mensagem resumida",
  "errors": [
    {
      "code": "ERROR_CODE",
      "message": "Mensagem resumida",
      "details": "Detalhes"
    }
  ],
  "timestamp": "2026-03-14T10:00:00Z",
  "traceId": "..."
}
```

### Bulk no backend atual

```json
{
  "status": "partial_success",
  "message": "Upload em lote processado com sucesso parcial",
  "timestamp": "2026-03-14T10:00:00Z",
  "data": {
    "totalProcessed": 3,
    "totalSuccess": 2,
    "totalFailed": 1,
    "results": []
  }
}
```

Semântica operacional:
- `status: "partial_success"` é o estado canônico para `207 Multi-Status`.
- `status: "failure"` fica reservado para falha total do lote.

Exceção arquitetural registrada:
- `partial_success` não pertence ao shape canônico hoje documentado para `RestApiResponse`.
- neste módulo ele deve ser tratado como exceção deliberada e temporariamente aceita, não como evidência de convergência plena com o padrão Praxis.
