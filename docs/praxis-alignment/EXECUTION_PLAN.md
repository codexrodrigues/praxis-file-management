# Execution Plan

## Objetivo

Alinhar `praxis-file-management` ao ecossistema Praxis de forma progressiva. O backend já foi levado ao contrato final e o próximo passo prático é alinhar o `praxis-files-upload` a esse shape.

## Estratégia

Ordem recomendada:
1. estabilizar o backend com contrato final claro;
2. adaptar o consumidor Angular;
3. só então evoluir convergência mais profunda com a plataforma;
4. por último discutir fluxo metadata-driven nativo.

## Fase 1

Nome: estabilização da trilha especializada
Status atual: concluída

Objetivo:
- consolidar endpoints, request shape e limites aceitos sem refatorar o core.

Entregas concluídas:
- README corrigido;
- documentação de retomada criada;
- alias `POST /api/files/bulk` implementado;
- suporte a `metadata` e `conflictPolicy` no upload simples;
- suporte a arrays homogêneos no request de bulk;
- matriz de compatibilidade registrada.

Decisão mantida:
- suporte real por arquivo no bulk fica adiado.

## Fase 2

Nome: alinhamento do backend com padrões de plataforma
Status atual: backend concluído; adaptação do consumidor pendente

Objetivo:
- fazer o módulo web se comportar como peça mais coerente com a Praxis.

Entregas concluídas no backend:
- `ApiEnvelopeFactory` centraliza sucesso em `{ status, message, data, timestamp }`;
- `ErrorResponse` centraliza erro em `{ status, message, errors, timestamp, traceId }`;
- `ConfigController`, `FileController` e `MonitoringController#getVersion` usam o shape final;
- OpenAPI e README foram revisados nos pontos críticos;
- a documentação principal de retomada foi atualizada.

Próxima evolução desta fase:
- adaptar o `praxis-files-upload` ao contrato final do backend;
- revisar tipos, serviços, mapeamento de erro e eventos;
- só depois discutir `links` ou maior aproximação com `RestApiResponse`.

## Fase 3

Nome: convergência ao fluxo canônico metadata-driven
Status atual: não iniciado

Objetivo:
- tornar upload uma capacidade nativa do contrato Praxis.

Dependências:
- backend e frontend já integrados no contrato final;
- decisão explícita de convergência total.

## Ordem interna recomendada

1. Congelar contrato backend.
2. Corrigir documentação e exemplos.
3. Endurecer testes de contrato do backend.
4. Adaptar `praxis-files-upload`.
5. Alinhar governança e convenções de plataforma.
6. Só depois discutir `FieldControlType.FILE_UPLOAD`.

## Progresso atual

Concluído:
1. Congelar contrato backend.
2. Corrigir documentação e exemplos.
3. Consolidar o envelope final do backend.

Em andamento:
4. Endurecer testes de contrato do backend.
5. Preparar adaptação do `praxis-files-upload`.

Próximo passo recomendado:
- iniciar a etapa Angular assim que a última rodada de testes de contrato do backend estiver fechada.

## Riscos atuais

- abrir a etapa Angular com documentação ainda ambígua;
- manter documentos históricos como se fossem checkpoint ativo;
- adaptar o frontend sem contrato de erro claramente testado.

## Recomendação final

Marco atual:
- backend final consolidado;
- próximo objetivo: integração real com `praxis-files-upload`.

Marco seguinte:
- só depois da integração backend/frontend, discutir aproximação adicional com `RestApiResponse` e fluxo metadata-driven.
