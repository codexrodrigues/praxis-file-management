# Future Migration Proposal

## Objetivo

Definir o próximo marco de evolução do contrato sem executar a migração agora.

Diretriz já aceita:
- manter `ErrorResponse` como contrato público transitório expandido neste ciclo;
- adiar a aproximação mais forte de `RestApiResponse` para uma migração controlada futura.

## Pergunta arquitetural central

Quando o módulo estiver estável o suficiente, qual será o passo seguinte mais seguro?

Resposta recomendada:
- reduzir gradualmente o protagonismo de `ErrorResponse` no contrato público;
- fazer `errors[]` virar a estrutura principal de erro;
- manter aliases legados (`code`, `error`, `details`) por um ciclo de compatibilidade;
- só depois avaliar remoção ou rebaixamento formal de `ErrorResponse`.

## Estratégia recomendada

### Etapa futura 1

Nome:
- consolidar `errors[]` como fonte canônica de erro

Objetivo:
- garantir que toda falha já seja semanticamente completa dentro de `errors[]`

Ações:
- padronizar todos os handlers para sempre preencher `errors[]`
- garantir que `code`, `error`, `message` e `details` no corpo raiz sejam derivados da primeira entrada de `errors[]`
- documentar explicitamente que `errors[]` é a estrutura preferencial para novos consumidores

Risco:
- baixo, porque não remove campos existentes

### Etapa futura 2

Nome:
- rebaixar `ErrorResponse` para contrato de compatibilidade

Objetivo:
- tornar o shape de erro mais próximo da semântica Praxis sem quebrar clientes atuais

Ações:
- manter `ErrorResponse` como espelho serializado do envelope de falha
- atualizar OpenAPI/examples para enfatizar `status/message/errors/timestamp`
- marcar `code`, `error` e `details` de raiz como campos legados de compatibilidade

Risco:
- médio-baixo, principalmente documental e de expectativa de consumidores

### Etapa futura 3

Nome:
- avaliar adoção parcial de `RestApiResponse`

Objetivo:
- aproximar o módulo do contrato padrão da plataforma sem romper compatibilidade de upload

Ações:
- introduzir `links` quando fizer sentido
- revisar se `success` ainda precisa existir publicamente
- estudar adaptação local para `RestApiResponse` em endpoints não especializados
- preservar `results/stats` do bulk enquanto `praxis-files-upload` depender deles

Risco:
- médio, porque começa a tocar mais diretamente a superfície pública

## O que não fazer

1. Não remover `code`, `error` ou `details` do corpo raiz no próximo ciclo.
2. Não trocar todo o módulo diretamente para `RestApiResponse`.
3. Não mexer no contrato de `presign`.
4. Não mexer no contrato operacional de `health` e `metrics` nesta frente.

## Critério para iniciar esta migração futura

Só iniciar quando estes itens estiverem verdadeiros:
- contrato transitório atual estiver congelado e aceito pelos consumidores;
- não houver trabalho urgente de estabilização em `bulk`;
- documentação pública e testes estiverem consistentes com o shape transitório;
- houver decisão explícita de iniciar aproximação adicional com o padrão Praxis.

## Backlog curto sugerido para o próximo marco

1. Declarar `errors[]` como estrutura preferencial para novos consumidores.
2. Revisar OpenAPI/examples para refletir isso.
3. Padronizar todos os handlers e respostas de falha restantes nesse modelo.
4. Classificar `ErrorResponse` formalmente como contrato transitório de compatibilidade.
