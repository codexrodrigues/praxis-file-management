# Transition Consolidation Checklist

## Objetivo

Fechar a fase atual de alinhamento transitório sem abrir refatoração estrutural adicional.

## Checklist

- [x] `ApiEnvelopeFactory` criado no módulo web
- [x] `ConfigController` migrado para o envelope transitório
- [x] `FileController` migrado para o envelope transitório
- [x] `GlobalExceptionHandler` alinhado ao mesmo eixo de envelope
- [x] `ErrorResponse` expandido para o contrato transitório
- [x] `MonitoringController#getVersion` migrado
- [x] bulk mantém compatibilidade com `results` e `stats`
- [x] bulk mantém limite explícito para arrays heterogêneos
- [x] `presign` marcado como exceção deliberada
- [x] exceções operacionais (`health`, `metrics`) marcadas como fora de escopo desta etapa
- [x] `ENVELOPE_ALIGNMENT.md` consolidado com o shape transitório real
- [x] checkpoints de retomada atualizados

## Exceções deliberadas desta fase

1. `POST /api/files/upload/presign`
- permanece com contrato raw de transporte

2. endpoints operacionais de monitoramento
- `health`
- `metrics`
- permanecem com payload operacional próprio

3. opções distintas por arquivo no bulk
- continuam fora de escopo

## Critério de saída

Esta fase pode ser considerada encerrada quando:
- não houver mais drift entre código e documentação do contrato transitório;
- os consumidores atuais continuarem compatíveis;
- a próxima decisão passar a ser arquitetural, não de estabilização básica.
