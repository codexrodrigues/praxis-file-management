# Compatibility Matrix

Última atualização: 2026-03-14
Escopo: estado atual entre `praxis-file-management` e `@praxisui/files-upload`
Objetivo: registrar com precisão o que ainda quebra ou precisa ser adaptado antes da etapa Angular

## Resumo executivo

Depois do corte do contrato backend, a integração deixou de ser parcialmente compatível e passou a depender de adaptação explícita no frontend.

Classificação geral:
- Caminhos HTTP: compatível
- Payload de config: incompatível no envelope tipado do frontend, mas de baixo impacto funcional
- Payload de presign: compatível
- Payload de upload simples: incompatível
- Payload de upload em lote: incompatível
- Envelope de erro: incompatível

## Endpoints

| Operação | Frontend atual espera | Backend atual expõe | Status | Observação |
|---|---|---|---|---|
| Upload simples | `POST {baseUrl}/upload` | `POST /api/files/upload` | Compatível | caminho encaixa |
| Upload em lote | `POST {baseUrl}/bulk` | `POST /api/files/bulk` e `POST /api/files/upload/bulk` | Compatível | alias preservado |
| Presign | `POST {baseUrl}/upload/presign?filename=...` | `POST /api/files/upload/presign?filename=...` | Compatível | contrato técnico raw preservado |
| Config efetiva | `GET {baseUrl}/config` | `GET /api/files/config` | Compatível | caminho e ETag encaixam |

## Configuração efetiva

### Frontend

Tipos em:
- [Config.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/types/Config.ts)
- [config.service.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/config/config.service.ts)

Estrutura atualmente modelada:

```json
{
  "success": true,
  "timestamp": "2025-08-22T12:34:56.789Z",
  "data": {
    "options": {},
    "bulk": {},
    "rateLimit": {},
    "quotas": {},
    "messages": {},
    "metadata": {}
  }
}
```

### Backend

Tipos em:
- [EffectiveUploadConfigRecord.java](/mnt/d/Developer/praxis-plataform/praxis-file-management/praxis-files-api/src/main/java/br/com/praxis/filemanagement/api/dtos/EffectiveUploadConfigRecord.java)
- [ConfigController.java](/mnt/d/Developer/praxis-plataform/praxis-file-management/praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/controller/ConfigController.java)

Estrutura atual:

```json
{
  "status": "success",
  "message": "Configuração efetiva recuperada com sucesso",
  "timestamp": "2025-08-22T12:34:56.789Z",
  "data": {
    "options": {},
    "bulk": {},
    "rateLimit": {},
    "quotas": {},
    "messages": {},
    "metadata": {}
  }
}
```

Status:
- Incompatível no tipo declarado do frontend.
- Funcionalmente próximo, porque o consumidor usa essencialmente `data`.

Observações:
- backend continua suportando ETag e headers `X-Tenant-Id` e `X-User-Id`;
- `ResponseEnvelope<T>` em `config.service.ts` precisa ser atualizado para `status` e `message`.

## Presigned upload

### Frontend

Tipo esperado em:
- [files-api-client.service.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/services/files-api-client.service.ts)

Estrutura:

```json
{
  "uploadUrl": "https://...",
  "headers": {},
  "fields": {}
}
```

### Backend

Implementação em:
- [FileController.java](/mnt/d/Developer/praxis-plataform/praxis-file-management/praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/controller/FileController.java)

Status:
- Compatível.

## Upload simples

### Frontend

Envio em:
- [files-api-client.service.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/services/files-api-client.service.ts)

Resposta modelada em:
- [Upload.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/types/Upload.ts)
- [praxis-files-upload.component.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/components/files-upload/praxis-files-upload.component.ts)

Estrutura atualmente consumida:

```json
{
  "file": {
    "id": "uuid",
    "fileName": "documento.pdf",
    "contentType": "application/pdf",
    "fileSize": 1048576,
    "uploadedAt": "2025-07-10T23:15:30.123Z",
    "tenantId": "tenant",
    "scanStatus": "CLEAN",
    "metadata": {}
  }
}
```

### Backend

Estrutura atual:

```json
{
  "status": "success",
  "message": "Upload realizado com sucesso",
  "timestamp": "2025-07-10T23:15:30.123Z",
  "data": {
    "originalFilename": "documento.pdf",
    "serverFilename": "doc_a1b2c3d4.pdf",
    "fileId": "550e8400-e29b-41d4-a716-446655440000",
    "fileSize": 1048576,
    "mimeType": "application/pdf",
    "uploadTimestamp": "2025-07-10T23:15:30.123Z"
  }
}
```

Status:
- Incompatível.

Diferenças principais:
- frontend espera `resp.file`;
- backend entrega `resp.data`;
- o shape interno do arquivo também diverge em nomenclatura.

## Upload em lote

### Frontend

Envio em:
- [files-api-client.service.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/services/files-api-client.service.ts)

Resposta modelada em:
- [Upload.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/types/Upload.ts)
- [praxis-files-upload.component.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/components/files-upload/praxis-files-upload.component.ts)

Estrutura atualmente consumida:

```json
{
  "results": [
    {
      "fileName": "documento.pdf",
      "status": "SUCCESS",
      "file": {},
      "error": {}
    }
  ],
  "stats": {
    "total": 1,
    "succeeded": 1,
    "failed": 0
  }
}
```

### Backend

Estrutura atual:

```json
{
  "status": "partial_success",
  "message": "Upload em lote processado com sucesso parcial",
  "timestamp": "2025-07-21T20:15:35.456Z",
  "data": {
    "totalProcessed": 3,
    "totalSuccess": 2,
    "totalFailed": 1,
    "totalCancelled": 0,
    "wasFailFastTriggered": false,
    "overallSuccess": false,
    "processingTimeMs": 5333,
    "totalSizeBytes": 3145728,
    "successRate": "66.67%",
    "results": [
      {
        "fileName": "documento.pdf",
        "status": "SUCCESS",
        "file": {}
      }
    ]
  }
}
```

Status:
- Incompatível.

Diferenças principais:
- frontend lê `results` e `stats` na raiz;
- backend agora entrega `data.results` e não entrega `stats`;
- backend usa `207 Multi-Status` e `status: "partial_success"` para sucesso parcial; `data.overallSuccess` segue como apoio.

Risco operacional remanescente:
- ainda não existe teste automatizado cross-repo entre este backend e `@praxisui/files-upload`; o handoff continua dependendo desta matriz e da futura adaptação Angular.

## Erros

### Frontend

Tipo esperado:
- [Errors.ts](/mnt/d/Developer/praxis-plataform/praxis-ui-angular/projects/praxis-files-upload/src/lib/types/Errors.ts)

Shape atual no frontend:

```json
{
  "code": "ERROR_CODE",
  "message": "Mensagem",
  "details": "...",
  "traceId": "..."
}
```

### Backend

Tipo:
- [ErrorResponse.java](/mnt/d/Developer/praxis-plataform/praxis-file-management/praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/error/ErrorResponse.java)

Shape atual no backend:

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

Status:
- Incompatível.

Observações:
- o frontend precisa passar a ler `errors[0]` como fonte primária;
- `ErrorMapperService` e a lógica de quota/rate-limit ainda dependem de `code` na raiz.

## Headers e contexto

| Tema | Frontend | Backend | Status | Observação |
|---|---|---|---|---|
| Tenant | configurável via `headers.tenantHeader` | `X-Tenant-Id` | Compatível | baixo risco |
| User | configurável via `headers.userHeader` | `X-User-Id` | Compatível | baixo risco |
| ETag config | suportado | suportado | Compatível | bom encaixe |

## Divergências prioritárias antes da etapa Angular

1. `UploadResponse` precisa migrar de `file` para envelope com `data`.
2. `BulkUploadResponse` precisa migrar de `results/stats` na raiz para envelope com `data.results`.
3. `ErrorResponse` do Angular precisa passar a ler `errors[0]`.
4. `config.service.ts` precisa alinhar o envelope tipado a `status` e `message`.

## Recomendação imediata

1. Não voltar a compatibilizar o backend.
2. Adaptar o Angular ao contrato final já estabilizado.
3. Manter esta matriz como fonte de verdade da lacuna backend/frontend até a conclusão da etapa Angular.
