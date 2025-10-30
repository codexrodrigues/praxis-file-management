# Relatório de Consistência – praxis-files

## Visão geral da arquitetura

```text
Cliente
  │
  ├─> RateLimitingFilter
  │      (valida limites por IP)
  │
  └─> FileController
          └─> FileService (core)
                ├─> InputValidationService
                ├─> MagicNumberValidator
                ├─> VirusScanningService
                ├─> ThreadSafeFileNamingService
                ├─> AtomicFileOperationService
                ├─> FileUploadMetricsService / SecurityAuditLogger
                └─> QuotaService (controle de cotas)
```

Módulos:

| Módulo | Função principal |
|-------|------------------|
| **praxis-files-api** | DTOs, enums e contratos do serviço de arquivos |
| **praxis-files-core** | Regras de negócio: validação, segurança, métricas e operações atômicas |
| **praxis-files-web** | Exposição HTTP, filtros e handlers de erro |
| **praxis-files-starter** | Auto-configuração Spring Boot (core + web) |
| **praxis-files-app** | Aplicação exemplo com configurações de demonstração |

*Filter de rate limiting reconhece IP real via `X-Forwarded-For` quando o proxy é declarado em `trustedProxies`.*
*Endpoints de monitoramento exigem role `MONITORING` e podem ser desativados com `file.management.monitoring.enabled=false`.*

## Fluxo E2E de upload
1. Requisição `POST /api/files` chega e passa pelo **RateLimitingFilter** (com resolução de IP real).
2. **FileController** recebe o `MultipartFile` e delega ao `FileService`.
3. `InputValidationService` verifica nome, tamanho e campos obrigatórios.
4. `MagicNumberValidator` confirma o tipo real do arquivo.
5. `VirusScanningService` (opcional) consulta ClamAV e envia arquivos infectados para um diretório de quarentena quando habilitado.
6. `ThreadSafeFileNamingService` aplica política de nome (`RENAME/REJECT/OVERWRITE`).
7. `AtomicFileOperationService` grava o arquivo de forma transacional.
8. Métricas e auditoria são registradas; resposta padronizada é retornada.

## Endpoints
| Método | Caminho | Descrição resumida | Códigos |
|-------|---------|-------------------|---------|
| POST | `/api/files` | Upload simples | 201, 400, 415, 429 |
| POST | `/api/files/upload/bulk` | Upload múltiplo | 201, 207, 400, 413, 429 |
| POST | `/api/files/upload/presign` | URL pré-assinada (PoC) | 200 |
| GET  | `/file-management/monitoring/health` | Saúde do sistema | 200, 503, 500 |
| GET  | `/file-management/monitoring/metrics` | Métricas filtradas | 200, 500 |
| GET  | `/file-management/monitoring/status` | Status resumido | 200 |
| GET  | `/file-management/monitoring/ping` | Ping simples | 200 |
| GET  | `/file-management/monitoring/version` | Versão da lib | 200 |

*Os endpoints de monitoramento requerem autenticação com role `MONITORING`.*

## Matriz de testes
Ver scripts em `tests/matrix.http` e `tests/run-scenarios.sh`.

| Cenário | Script | Resultado esperado |
|---------|--------|-------------------|
| Upload válido | `happy_path` | 201 |
| Arquivo vazio | `empty_file` | 400 |
| Tamanho excedido | `too_large` | 413 |
| MIME inválido | `unsupported_media` | 415 |
| Rate limit | `rate_limit` | 429 |
| Cota excedida | `quota_exceeded` | 429 |
| Antivírus (EICAR) | `antivirus` | 400 |
| Nome duplicado RENAME | `duplicate_name_rename` | 201 com novo nome |
| Nome duplicado REJECT | `duplicate_name_reject` | 409/400 |
| Nome duplicado OVERWRITE | `duplicate_name_overwrite` | 200/201 |
| Path traversal | `path_traversal` | 400 |
| Web desabilitada | `web_off` | 404/503 |
| Monitoramento | `monitoring` | 200/503 |

## Gaps e recomendações
* **Dependências externas**: build Maven falhou por falta de acesso à internet; pipeline CI deve garantir cache ou repositório interno.
* **Observabilidade**: métricas `file_errors_total` cobrem razões de erro, incluindo cotas.
* **UX**: documentação expandida com exemplos de erro e suporte a URLs pré-assinadas.

