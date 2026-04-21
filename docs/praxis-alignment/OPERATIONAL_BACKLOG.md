# Operational Backlog

## Visão geral

Este backlog organiza o trabalho por épicos, histórias e tarefas técnicas, separados por repositório.

Status padrão inicial:
- `Todo`: ainda não iniciado
- `Doing`: em andamento
- `Done`: concluído
- `Blocked`: depende de decisão externa

## Épico 1

Nome: estabilizar a integração atual de upload
Fase: 1
Status: Done

### Repositório `praxis-file-management`

História: corrigir drift entre documentação e endpoints reais
Status: Done

Tarefas:
- revisar README e exemplos de quick start;
- alinhar caminhos públicos reais de upload, bulk, presign e config;
- revisar exemplos de payload de sucesso, erro e config;
- consolidar documentação de headers, auth e comportamento padrão.

História: congelar contrato backend consumido pelo frontend
Status: Done

Tarefas:
- mapear respostas reais de `upload`, `bulk`, `presign` e `config`;
- documentar campos obrigatórios e opcionais;
- identificar diferenças entre DTOs Java e tipos TypeScript;
- criar tabela de compatibilidade de contrato com `@praxisui/files-upload`.

História: consolidar posicionamento de produto e publicação
Status: Todo

Tarefas:
- revisar groupId e naming público;
- revisar `distributionManagement` legado;
- documentar decisão de identidade do módulo dentro do ecossistema Praxis.

### Repositório `praxis-ui-angular`

História: estabilizar contrato de consumo do `praxis-files-upload`
Status: Done

Tarefas:
- revisar tipos `FileMetadata`, `ErrorResponse` e `EffectiveUploadConfig`;
- garantir cobertura de testes para payloads reais do backend;
- remover fallback legado e shape transitório do cliente Angular;
- validar docs do componente contra comportamento real.

Tarefas concluídas:
- compatibilidade do path de bulk via backend;
- suporte inicial de `metadata` e `conflictPolicy` no upload simples;
- compatibilidade do request de bulk para arrays homogêneos;
- testes focados de controller validados no backend.
- adaptação de `UploadResponse` ao envelope com `data`;
- adaptação de `BulkUploadResponse` ao envelope com `data.results`;
- adaptação de `ResponseEnvelope<T>` de config para `status` e `message`;
- revisão do `ErrorMapperService` para `errors[0]`;
- revisão dos eventos de sucesso simples e bulk;
- limpeza de docs e exemplos para remover legado.

### Checkpoint operacional do Épico 1

Estado atual:
- documentação e contrato do backend já permitem retomada após crash;
- backend e Angular já operam com shape final;
- o próximo item é automação de validação, não nova compatibilidade manual.

Próxima história recomendada:
- criar validação automatizada cross-repo do contrato final backend/frontend.

Tarefas técnicas sugeridas para a próxima rodada:
- consolidar um smoke backend-only reproduzível para `config`, `upload`, `bulk`, `quota` e `rate limit`;
- plugar esse smoke ao fluxo operacional do repositório;
- adicionar validação cross-repo que exercite o `praxis-files-upload` contra o backend real;
- documentar explicitamente o conjunto mínimo de checks antes de liberar nova alteração de contrato.

## Épico 2

Nome: alinhar `praxis-file-management` aos padrões da plataforma
Fase: 2
Status: Doing

### Repositório `praxis-file-management`

História: alinhar envelopes de resposta ao padrão Praxis
Status: Done

Tarefas:
- definir o shape final do backend;
- adaptar controllers de upload, config e monitoring;
- revisar tratamento de erro e exceções globais;
- consolidar OpenAPI, README e checkpoints principais.

Tarefas concluídas:
- helper interno `ApiEnvelopeFactory` criado;
- `ConfigController`, `FileController` e `MonitoringController#getVersion` migrados para o helper;
- `GlobalExceptionHandler` e `ErrorResponse` ajustados ao contrato final de erro;
- OpenAPI e README revisados nos pontos críticos;
- `ENVELOPE_ALIGNMENT.md`, `STATUS.md`, `EXECUTION_PLAN.md` e `COMPATIBILITY_MATRIX.md` atualizados para o estado pós-corte.

História: alinhar estratégia de erro ao ecossistema Praxis
Status: Done

Tarefas:
- revisar `GlobalExceptionHandler`;
- mapear erros para estrutura compatível com `RestApiResponse.errors`;
- definir códigos de erro padronizados;
- alinhar mensagens e categorias para uso em frontend e IA.

Tarefas concluídas:
- `GlobalExceptionHandler` alinhado ao envelope final;
- `ErrorResponse` consolidado em `errors[]`;
- `RateLimitingFilter` ajustado para devolver `429` padronizado ainda no filtro;
- smoke HTTP confirmou comportamento real sem Angular/Playwright.

Próximo passo real:
- automatizar a validação do contrato final antes de mexer novamente no backend.

História: alinhar segurança e auto-configuração a hosts Praxis
Status: Todo

Tarefas:
- revisar `SecurityFilterChain` default;
- remover comportamento intrusivo incompatível com apps host;
- alinhar CORS, auth e handlers com práticas dos starters centrais;
- documentar estratégia de override pelo host.

### Repositório `praxis-config-starter`

História: suportar governança de upload via config runtime
Status: Todo

Tarefas:
- definir chaves canônicas para config de upload;
- permitir persistência por tenant/user/env;
- alinhar `componentType` e `componentId` do upload com o store transacional;
- documentar fallback e versionamento.

História: suportar melhor contexto de upload para IA
Status: Todo

Tarefas:
- registrar metadata relevante do widget de upload;
- garantir ingestão e busca contextual do componente;
- definir capacidades e templates aplicáveis ao caso de upload;
- revisar como a IA descobrirá limites, quotas e estratégias.

### Repositório `praxis-ui-angular`

História: alinhar widget de upload aos contratos de plataforma
Status: Done

Tarefas:
- adaptar cliente Angular ao contrato backend final;
- revisar `ErrorMapperService` para novos códigos/categorias;
- alinhar config editor e storage à convenção canônica;
- revisar UX de sucesso parcial em bulk.

Tarefas concluídas:
- cliente Angular migrado para `data` e `data.results`;
- mapeamento de erro alinhado a `errors[0]`;
- docs e exemplos limpos sem legado;
- build de produção do pacote validado localmente.

## Épico 3

Nome: convergir upload ao fluxo canônico metadata-driven
Fase: 3
Status: Todo

### Repositório `praxis-metadata-starter`

História: definir contrato `x-ui` oficial para upload
Status: Todo

Tarefas:
- especificar propriedades de upload em `@UISchema`;
- definir mapeamento para limites, strategy, quotas e detalhes de UI;
- documentar exemplos de DTO com campo de upload avançado;
- alinhar resolver OpenAPI e docs filtradas.

História: publicar upload como capacidade nativa no `/schemas/filtered`
Status: Todo

Tarefas:
- revisar `CustomOpenApiResolver`;
- revisar payload retornado por `ApiDocsController`;
- garantir exposição consistente de metadados necessários ao renderer avançado.

### Repositório `praxis-ui-angular`

História: unificar `FieldControlType.FILE_UPLOAD` com componente avançado
Status: Todo

Tarefas:
- definir estratégia entre substituição ou composição;
- conectar `dynamic-form` ao wrapper avançado `pdx-material-files-upload`;
- migrar gradualmente do renderer básico;
- revisar form builder, metadata editor e testes.

História: eliminar duplicidade entre widget especializado e campo canônico
Status: Todo

Tarefas:
- revisar `praxis-files-upload` e `pdx-material-file-upload`;
- decidir qual será o renderer oficial;
- alinhar metadata registry e field selector registry;
- remover trilhas redundantes após migração.

### Repositório `praxis-file-management`

História: expor backend de upload como capability nativa do contrato Praxis
Status: Todo

Tarefas:
- alinhar endpoints e contratos a um modelo orientado por schema;
- reduzir dependência de contrato ad hoc para descoberta de configuração;
- garantir consumo coerente pelo fluxo metadata-driven.

### Repositório `praxis-api-quickstart`

História: criar referência oficial de upload no host de demonstração
Status: Todo

Tarefas:
- adicionar exemplo real de campo/upload avançado;
- demonstrar `/schemas/filtered` com upload;
- demonstrar persistência de config de upload;
- demonstrar fluxo de IA sobre upload.

## Dependências entre épicos

1. Épico 1 antes do Épico 2.
2. Épico 2 antes do Épico 3.
3. A unificação do `FILE_UPLOAD` depende da estabilização do contrato backend.

## Ordem recomendada por repositório

1. `praxis-file-management`
2. `praxis-ui-angular`
3. `praxis-config-starter`
4. `praxis-metadata-starter`
5. `praxis-api-quickstart`

## Checklist de kickoff da implementação

- validar este backlog com stakeholders;
- escolher estratégia de compatibilidade;
- definir owner técnico por repositório;
- transformar histórias priorizadas em issues rastreáveis;
- começar pelo Épico 1.
