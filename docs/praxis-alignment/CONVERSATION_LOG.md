# Conversation Log

## Objetivo da conversa

Investigar por que `praxis-file-management` parece não se encaixar bem na plataforma Praxis e construir um plano concreto de alinhamento.

## Linha do tempo resumida

### Etapa 1

Análise inicial de `praxis-file-management`.

Achados:
- projeto multi-módulo Spring independente;
- documentação e identidade de produto próprias;
- contratos HTTP próprios;
- sinais de desalinhamento com o núcleo metadata-driven da plataforma.

Conclusão preliminar:
- o projeto se comporta mais como componente satélite do ecossistema do que como módulo orgânico da plataforma.

### Etapa 2

Estudo aprofundado dos projetos Java principais da plataforma:
- `praxis-metadata-starter`
- `praxis-config-starter`
- `praxis-api-quickstart`

Conclusão:
- o fluxo canônico da Praxis Java é:
  `DTO anotado -> OpenAPI + x-ui -> /schemas/filtered -> UI runtime -> config/IA`
- `metadata-starter` é a fonte de verdade do contrato;
- `config-starter` governa configuração runtime, RAG e IA em cima desse contrato;
- `api-quickstart` é o host de referência que demonstra esse modelo.

### Etapa 3

Estudo aprofundado do componente dinâmico de upload em `praxis-ui-angular`.

Achados:
- existe um fluxo canônico básico de campo dinâmico para `FILE_UPLOAD`;
- existe um fluxo avançado especializado via `praxis-files-upload`;
- o componente avançado já participa do ecossistema de widgets Praxis:
  - metadata registry;
  - settings panel;
  - config storage;
  - adapter e capabilities de IA;
  - wrapper de formulário.

Conclusão:
- o frontend avançado de upload está mais alinhado à plataforma do que o backend de arquivos;
- ainda existe duplicidade entre o campo canônico básico e o widget avançado.

### Etapa 4

Síntese arquitetural final.

Conclusões:
- há integração funcional real entre `praxis-file-management` e `@praxisui/files-upload`;
- essa integração existe por trilha lateral, não pelo centro arquitetural da plataforma;
- a convergência deve ser feita por fases, começando por estabilidade e contrato.

### Etapa 5

Início operacional do Marco A.

Entregas produzidas:
- plano por fases;
- backlog operacional por repositório;
- matriz de compatibilidade backend/frontend.

Decisão:
- iniciar com congelamento documental do contrato atual;
- evitar mudanças funcionais antes de resolver drift de documentação e bulk path.

### Etapa 6

Primeiras mudanças concretas do Marco A.

Entregas realizadas:
- documentação pública atualizada para refletir endpoints reais;
- compatibilidade backend adicionada para `POST /api/files/bulk`;
- upload simples atualizado para aceitar `metadata` e `conflictPolicy`;
- response de bulk atualizado para expor `results` e `stats` no corpo raiz;
- request de bulk atualizado para aceitar arrays homogêneos de `metadata` e `conflictPolicy`;
- testes de controller executados com sucesso no módulo `praxis-files-web`.

Comando de validação executado com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=FileControllerMimeTypeTest,FileControllerBulkUploadTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Conclusão desta etapa:
- o Marco A saiu do modo apenas documental;
- já existem entregas funcionais locais;
- o próximo gap prioritário deixou de ser compatibilidade superficial e virou limitação estrutural do core.

## Decisões de planejamento tomadas

1. Não iniciar por refatoração grande.
2. Trabalhar em 3 fases.
3. Priorizar:
- documentação;
- contrato;
- envelopes de resposta/erro;
- governança de configuração.
4. Deixar a convergência total do `FieldControlType.FILE_UPLOAD` para a fase final.

## Artefatos produzidos nesta conversa

- diagnóstico arquitetural do backend Java Praxis;
- diagnóstico do `praxis-file-management`;
- diagnóstico do `praxis-files-upload`;
- plano de execução por fases;
- backlog operacional por repositório;
- documentos de retomada neste diretório.

## Estado ao encerrar esta rodada

Planejamento concluído.
Implementação do Marco A iniciada e parcialmente concluída.

## Instrução de retomada

Se esta investigação precisar continuar depois:

1. revisar `STATUS.md`;
2. validar `EXECUTION_PLAN.md`;
3. usar `OPERATIONAL_BACKLOG.md` como base do kickoff;
4. preservar este arquivo como histórico da decisão arquitetural.
5. continuar pelo próximo item pendente do Marco A: normalização semântica do bulk.

## Checkpoint adicional de retomada

Arquivos já alterados funcionalmente nesta rodada:
- `praxis-files-web/src/main/java/br/com/praxis/filemanagement/web/controller/FileController.java`
- `praxis-files-web/src/test/java/br/com/praxis/filemanagement/web/controller/FileControllerBulkUploadTest.java`
- `praxis-files-web/src/test/java/br/com/praxis/filemanagement/web/controller/FileControllerMimeTypeTest.java`
- `praxis-files-web/src/test/java/br/com/praxis/filemanagement/web/controller/FileControllerPathTraversalTest.java`
- `README.md`
- `docs/praxis-alignment/COMPATIBILITY_MATRIX.md`

Resultado funcional consolidado:
- backend aceita alias `POST /api/files/bulk`;
- upload simples aceita `metadata` e `conflictPolicy`;
- bulk response expõe `results` e `stats` no corpo raiz;
- bulk request aceita arrays homogêneos de `metadata` e `conflictPolicy`;
- arrays heterogêneos retornam erro explícito para não mascarar limitação do serviço;
- testes focados de controller passaram localmente.

Próximo item recomendado sem reabrir contexto:
- seguir para o próximo marco, mantendo fora de escopo a refatoração do core para opções por arquivo no bulk.

## Decisão posterior registrada

O usuário aceitou encerrar o Marco A sem evoluir o core para opções distintas por arquivo no bulk.
Essa limitação foi mantida conscientemente para preservar o objetivo da etapa: estabilização e compatibilidade de baixo risco.

## Etapa 7

Início do próximo marco pelo alinhamento de envelope.

Entregas realizadas:
- diagnóstico das divergências em `ENVELOPE_ALIGNMENT.md`;
- criação de `ApiEnvelopeFactory` no módulo web;
- migração de `ConfigController` para o helper;
- migração dos fluxos principais de `FileController` para o helper sem quebrar `success`, `error`, `results` e `stats`.

Validação executada com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=ConfigControllerTest,FileControllerBulkUploadTest,FileControllerMimeTypeTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Conclusão desta etapa:
- o próximo marco já saiu do diagnóstico e entrou em implementação;
- o contrato público legado foi preservado;
- campos de transição (`status`, `message`, `errors`) começaram a aparecer por helper central.

## Etapa 8

Alinhamento do tratamento global de erro ao mesmo envelope transitório.

Entregas realizadas:
- `ErrorResponse` expandido para suportar `status`, `error` e `errors`;
- `GlobalExceptionHandler` ajustado para enriquecer erros pelo mesmo modelo do helper;
- contratos antigos (`code`, `message`, `details`, `traceId`) preservados.

Validação executada com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=GlobalExceptionHandlerTest,ConfigControllerTest,FileControllerBulkUploadTest,FileControllerMimeTypeTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Conclusão desta etapa:
- sucesso e erro já compartilham o mesmo eixo de transição no módulo web;
- o próximo passo deixou de ser infraestrutura de envelope e passou a ser expansão controlada desse padrão.

## Etapa 9

Expansão do envelope transitório para endpoint auxiliar.

Entregas realizadas:
- `MonitoringController#getVersion` migrado para `ApiEnvelopeFactory`;
- teste de monitoramento ajustado para validar `success`, `status`, `message` e `data`.

Validação executada com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=MonitoringControllerTest,GlobalExceptionHandlerTest,ConfigControllerTest,FileControllerBulkUploadTest,FileControllerMimeTypeTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Conclusão desta etapa:
- o padrão transitório já cobre sucesso principal, erro global e endpoint auxiliar;
- o próximo passo é fechar a revisão das respostas ad hoc remanescentes e documentar o shape transitório consolidado.

## Etapa 10

Consolidação documental do contrato transitório.

Entregas realizadas:
- `ENVELOPE_ALIGNMENT.md` atualizado com o shape transitório já implementado;
- exceções deliberadas registradas:
  - `presign`
  - endpoints operacionais de health/metrics;
- comentário de código adicionado em `createPresignedUpload` explicando por que o endpoint permanece raw.

Conclusão desta etapa:
- a etapa atual fica fechada com contrato transitório explícito;
- o próximo passo passa a ser uma decisão arquitetural sobre o destino final de `ErrorResponse`.

## Etapa 11

Fechamento operacional da fase transitória.

Entregas realizadas:
- checklist de consolidação criado em `history/HISTORICAL_TRANSITION_CONSOLIDATION_CHECKLIST.md`;
- índice dos documentos de alinhamento atualizado;
- README público alinhado ao shape transitório já implementado em `config`, `upload` e `bulk`.

Conclusão desta etapa:
- a fase atual fica encerrada com documentação, checklist e checkpoints consistentes;
- o próximo passo deixa de ser estabilização e passa a ser escolha arquitetural sobre a evolução futura do contrato.

## Etapa 12

Preparação do próximo marco sem implementação.

Entregas realizadas:
- proposta curta de migração futura criada em `history/HISTORICAL_FUTURE_MIGRATION_PROPOSAL.md`;
- direção recomendada registrada:
  - manter `ErrorResponse` neste ciclo
  - preparar a migração futura em torno de `errors[]`
  - evitar troca brusca para `RestApiResponse`

Conclusão desta etapa:
- o próximo marco já está definido em termos de direção técnica;
- não há necessidade de novas mudanças de código até a decisão de início da migração futura.

## Etapa 13

Nova prioridade definida: convergência maior com a plataforma Praxis.

Entregas realizadas:
- plano curto do próximo marco criado em `history/HISTORICAL_PRAXIS_CONVERGENCE_NEXT_MILESTONE.md`;
- direção operacional registrada:
  - `errors[]` vira centro semântico do erro
  - `ErrorResponse` permanece transitório
  - aproximação com `RestApiResponse` continua controlada, não brusca

Conclusão desta etapa:
- o próximo marco está pronto para execução;
- a prioridade deixou de ser estabilização e passou a ser convergência progressiva.

## Etapa 14

Primeira implementação do marco de convergência maior.

Entregas realizadas:
- `ErrorResponse` documentado como contrato transitório com `errors[]` preferencial;
- exemplos OpenAPI em `ConfigController` e `FileController` atualizados para refletir `status`, `error` e `errors[]`;
- README público atualizado para orientar novos consumidores a preferirem `errors[]`.

Validação executada com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=MonitoringControllerTest,GlobalExceptionHandlerTest,ConfigControllerTest,FileControllerBulkUploadTest,FileControllerMimeTypeTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Conclusão desta etapa:
- a convergência maior já começou sem ruptura de contrato;
- `errors[]` passou a ser preferencial em código exposto, schema e documentação.

## Etapa 15

Alinhamento dos exemplos auxiliares ao mesmo modelo de convergência.

Entregas realizadas:
- exemplos OpenAPI de `MonitoringController` atualizados para refletir:
  - `status`
  - `error`
  - `errors[]`
  - envelope de sucesso em `version`

Validação executada com sucesso:

```bash
./mvnw -q -pl praxis-files-web -am \
  -Dtest=MonitoringControllerTest,GlobalExceptionHandlerTest,ConfigControllerTest,FileControllerBulkUploadTest,FileControllerMimeTypeTest,FileControllerPathTraversalTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Conclusão desta etapa:
- a documentação exposta do módulo web está mais coerente com a direção de convergência;
- o próximo passo passa a ser decidir se o contrato público deve começar a rebaixar formalmente `code/error/details` de raiz para campos legados.

## Etapa 16

Fechamento do contrato final no consumidor Angular.

Entregas realizadas:
- `praxis-files-upload` limpo para consumir apenas o contrato final do backend;
- remoção de fallback legado de `file`, `results`, `stats`, `code` e `details` no root;
- revisão de docs, metadata e exemplos para eliminar narrativa de compatibilidade/transição;
- exemplo principal ajustado para separar claramente upload simples e bulk.

Validação executada com sucesso:

```bash
ng build praxis-files-upload --configuration production
```

Conclusão desta etapa:
- o frontend consumidor deixou de depender de shape transitório;
- backend e Angular passaram a compartilhar o mesmo contrato final sem legado.

## Etapa 17

Validação backend-only e correção de bugs reais pós-alinhamento.

Achados da validação por HTTP:
- `config`, `upload`, `bulk`, alias `/api/files/bulk` e quota estavam funcionais;
- havia bug nas restrições ad hoc de tipo:
  - `allowedExtensions` podia ser contornada;
  - `acceptMimeTypes` podia ser contornado;
- havia bug de rate limit:
  - segunda requisição retornava `500` em vez de `429`.

Correções realizadas:
- `LocalStorageFileService` ajustado para aplicar corretamente restrições isoladas e combinadas;
- `RateLimitingFilter` ajustado para responder `429` com envelope final e headers `X-RateLimit-*`;
- testes focados adicionados/ajustados em core e web.

Validação executada com sucesso:

```bash
/opt/maven/bin/mvn -f pom.xml -pl praxis-files-core -Dtest=FileUploadApiTest test

/opt/maven/bin/mvn -f pom.xml -pl praxis-files-web \
  -Dtest=br.com.praxis.filemanagement.web.filter.RateLimitingFilterTest test

/opt/maven/bin/mvn -f pom.xml install -DskipTests -Dmaven.compiler.parameters=true
```

Smoke backend-only confirmado:
- `GET /api/files/config` -> `200`
- upload simples com `allowedExtensions=["txt"]` e arquivo inválido -> `400`
- upload simples com `acceptMimeTypes=["application/pdf"]` e arquivo inválido -> `400`
- bulk com um item inválido -> `207 partial_success`
- rate limit excedido -> `429` com envelope final

Conclusão desta etapa:
- os últimos bugs encontrados no backend durante a retomada foram fechados;
- o próximo passo deixa de ser compatibilidade e passa a ser automação/governança do contrato.
