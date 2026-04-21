# Praxis Convergence Next Milestone

## Objetivo

Abrir o próximo marco com foco em convergência maior com a plataforma Praxis, sem quebrar a integração já estabilizada.

Prioridade assumida:
- aumentar a aderência ao modelo Praxis;
- manter compatibilidade suficiente para não romper o frontend atual;
- evitar migração brusca para `RestApiResponse`.

## Meta do marco

Tornar `errors[]` a estrutura canônica de erro no `praxis-file-management`, reduzindo o protagonismo do contrato legado sem removê-lo ainda.

## Resultado esperado

Ao final deste marco:
- novos consumidores devem olhar primeiro para `status`, `message` e `errors`;
- `ErrorResponse` deve existir como compatibilidade transitória;
- `code`, `error` e `details` de raiz devem ser tratados como espelho da primeira entrada de `errors[]`;
- a documentação pública deve refletir isso explicitamente.

## Escopo

Incluído:
- alinhamento semântico do contrato de erro;
- revisão de exemplos OpenAPI;
- revisão de documentação pública;
- padronização dos handlers e respostas de falha remanescentes;
- formalização de compatibilidade transitória.

Excluído:
- migração completa para `RestApiResponse`;
- remoção de `success`;
- mudança do contrato de `presign`;
- mudança do contrato operacional de `health` e `metrics`;
- refatoração do core para opções por arquivo no bulk.

## Plano curto

### Etapa 1

Nome:
- erro canônico por `errors[]`

Ações:
- declarar `errors[]` como estrutura preferencial para consumidores novos;
- garantir que todos os erros relevantes já sejam completos dentro de `errors[]`;
- garantir coerência entre raiz e primeira entrada de `errors[]`.

Saída:
- contrato de erro semanticamente estável.

### Etapa 2

Nome:
- rebaixamento formal de `ErrorResponse`

Ações:
- manter `ErrorResponse` como contrato público transitório;
- atualizar docs e exemplos para tratar `errors[]` como centro semântico;
- marcar `code`, `error` e `details` de raiz como compatibilidade.

Saída:
- documentação e OpenAPI alinhadas ao destino arquitetural.

### Etapa 3

Nome:
- preparação para aproximação futura com Praxis

Ações:
- revisar onde `status/message/data/errors` já estão suficientemente próximos do padrão Praxis;
- listar gaps restantes para futura aproximação com `RestApiResponse`;
- preservar exceções deliberadas.

Saída:
- base pronta para uma futura convergência adicional.

## Backlog executável

1. Atualizar `ENVELOPE_ALIGNMENT.md` para declarar `errors[]` como estrutura principal de erro.
2. Revisar exemplos OpenAPI em `FileController`, `ConfigController` e `MonitoringController`.
3. Revisar `ErrorResponse` e anotações/documentação para reforçar o papel transitório.
4. Garantir consistência do `GlobalExceptionHandler` entre todos os handlers.
5. Atualizar `README.md` com a nova leitura canônica do contrato de erro.
6. Registrar explicitamente as exceções fora do marco.

## Critério de conclusão

Este marco estará concluído quando:
- o contrato de erro estiver semanticamente centrado em `errors[]`;
- a documentação pública estiver coerente com isso;
- os aliases legados continuarem disponíveis;
- não houver mudança destrutiva para consumidores atuais.
