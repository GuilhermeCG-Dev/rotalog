# Análise: shared-types e api-contracts vs. contratos reais dos backends

Data: 2026-10-27

Escopo: `rotalog-frontend/libs/shared-types/src/index.ts` e `rotalog-frontend/libs/api-contracts/src/openapi.yaml`, comparados com os controllers, models e DTOs de `rotalog-api-frotas`, `rotalog-api-entregas` e `rotalog-api-notificacoes`.

A lib `api-contracts` tem só o `openapi.yaml`, sem TypeScript. O `shared-types` não tem nenhuma interface alinhada por inteiro com o backend.

## 1. Veiculo (api-frotas, `/api/veiculos`)

| shared-types | backend | problema |
|---|---|---|
| `ano` | `anoFabricacao` | nome diferente |
| `status: 'ativo'\|'inativo'\|'manutencao'` | `ATIVO\|INATIVO\|MANUTENCAO` | caixa diferente |
| `ultimaManutencao?` | não existe | campo inventado. Existe `GET /manutencoes/veiculo/{id}/ultima` |
| ausente | `dataAtualizacao` | faltando |
| `dataCadastro: string` | `dataCadastro` | OK. O TODO no código está errado, o backend já devolve camelCase |

O `openapi.yaml` repete esses erros. Ele usa `ano` em `VeiculoInput` e o enum em minúsculas. O backend espera `anoFabricacao`. O `quilometragem` está `number`, mas é `Long`, então `integer`.

## 2. Motorista (api-frotas, `/api/motoristas`)

Quase tudo diverge.

- **Só em shared-types:** `email`, `telefone`, `veiculo_id`, `ativo`. O backend não tem esses campos. Os FIXME na entidade Java confirmam que email, telefone e vínculo com veículo faltam.
- **Nome diferente:**
  - `numeroCnh` vira `cnh`.
  - `dataVencimentoCnh` vira `vencimentoCnh`, um `LocalDate`, então `YYYY-MM-DD` sem hora.
  - `ativo: boolean` vira `status: 'ATIVO'|'INATIVO'|'FERIAS'`.
- **Faltando em shared-types:** `categoriaCnh`, `status`, `dataCadastro`, `dataAtualizacao`.

## 3. Entrega (api-entregas, `/api/entregas`)

A interface está em snake_case com nomes em inglês. O backend usa snake_case em português.

| shared-types | backend |
|---|---|
| `driver_name` | `motorista_nome` |
| `vehicle_plate` | `veiculo_placa` |
| `origin_address` / `destination_address` | `origem_endereco` / `destino_endereco` |
| `origin_lat/lng` e `destination_lat/lng` | `origem_lat/lng` e `destino_lat/lng` |
| `distance_km` | `distancia_km` |
| `estimated_time_minutes` | `tempo_estimado_minutos` |
| `progress_percentage` | não existe |
| `events?: EntregaEvento[]` | `rastreamentos[]` |

- **Campos faltando:** `veiculo_modelo`, `motorista_id`, `peso_kg`, `observacoes`, `data_criacao`, `data_atualizacao`, `data_coleta`, `data_entrega`.
- **Status:** o backend usa `PENDENTE|ATRIBUIDA|EM_TRANSITO|ENTREGUE|CANCELADA`, em maiúsculas. O type tem minúsculas e `ATRIBUIDA` não existe nele. Além disso, o filtro `?status=` faz `toUpperCase()`.
- **Tipos numéricos:** o Sequelize devolve `DECIMAL` e `BIGINT` como string com o driver `pg`. Isso vale para `id`, `lat/lng`, `distancia_km` e `peso_kg`. O type declara `number`. Não executei a API, então confirmar com uma resposta real.
- **EntregaEvento:** a interface tem `timestamp`, `description` e `status`. O backend (`Rastreamento`) tem `id`, `entrega_id`, `latitude`, `longitude`, `evento`, `descricao` e `data_evento`. Nenhum nome bate.

## 4. Notificacao (api-notificacoes, `/api/notificacoes`)

- **`tipo`:** o type usa `'email'|'sms'|'webhook'`, mas esses valores são `canal`. O `tipo` real é um evento, como `ENTREGA_CRIADA`, em string livre.
- **`enviado_em`:** o campo real é `dataEnvio`, e o JSON vem em camelCase.
- **Faltando:** `canal`, `status`, `tentativas`, `maxTentativas`, `erroMensagem`, `servicoOrigem`, `referenciaId`, `dataCriacao`, `dataAtualizacao`.
- **`mensagem`:** no request é opcional quando há `variaveis`.
- **Sem interface alguma:**
  - `TemplateNotificacao`
  - `NotificacaoStats`
  - `NotificacaoRequest`
  - `ReenvioRequest`
- **`ConfiguracaoNotificacao`:** existe como modelo, mas não tem endpoint.

## 5. Interfaces que não existem em shared-types

- **`Manutencao`:**
  - Campos: `id`, `veiculoId`, `tipoManutencao`, `dataManutencao`, `quilometragemManutencao`, `custo`, `descricao`, `status`, `dataCriacao`, `dataAtualizacao`.
  - Tipos: `PREVENTIVA|CORRETIVA|REVISAO`.
  - Status: `PENDENTE|EM_ANDAMENTO|CONCLUIDA|CANCELADA`.
- **`Rastreamento`**, descrito na seção 3.
- **Resposta de `/rastreamento/pedido/:n`:** `{pedido, status, origem, destino, data_criacao, data_entrega, eventos}`.
- **Stats de entregas:** `{total, por_status[], gerado_em}`.
- **Stats de veículos:** uma string JSON com `{total, ativos, inativos, em_manutencao}`.
- **Respostas de erro:** são três formatos diferentes:
  - frotas devolve `{erro}`.
  - entregas devolve `{error, detalhes?}`.
  - notificações devolve `{error, detalhes}`.

## 6. Endpoints sem documentação no openapi

O openapi documenta 3 dos 56 endpoints reais: `GET/POST /api/veiculos` e `GET /api/entregas`.

**Endpoints inexistentes que estão documentados:**
- `/api/rastreamento/real-time` não existe. A rota `/:entregaId` o trataria como um id inválido.

**Gaps estruturais:**
- O openapi não declara auth, mas `/api/entregas` e `/api/frotas` exigem `authMiddleware`. `/api/rastreamento` é público.
- Os três servers usam o prefixo `/api`. O Java ganha esse prefixo pelo `context-path`.

**api-frotas (:8080), 26 faltando:**
- Veículos (8):
  - `GET /veiculos/{id}`
  - `GET /veiculos/placa/{placa}`
  - `GET /veiculos/status/{status}`
  - `PUT /veiculos/{id}`
  - `PATCH /veiculos/{id}/quilometragem`
  - `PATCH /veiculos/{id}/desativar`
  - `PATCH /veiculos/{id}/reativar`
  - `GET /veiculos/estatisticas`
- Motoristas (9):
  - `GET /motoristas`
  - `GET /motoristas/{id}`
  - `GET /motoristas/cnh/{cnh}`
  - `GET /motoristas/status/{status}`
  - `GET /motoristas/cnh-vencida`
  - `POST /motoristas`
  - `PUT /motoristas/{id}`
  - `PATCH /motoristas/{id}/desativar`
  - `PATCH /motoristas/{id}/reativar`
- Manutenções (9):
  - `GET /manutencoes`
  - `GET /manutencoes/{id}`
  - `GET /manutencoes/veiculo/{veiculoId}`
  - `GET /manutencoes/veiculo/{veiculoId}/ultima`
  - `GET /manutencoes/pendentes`
  - `POST /manutencoes`
  - `PATCH /manutencoes/{id}/iniciar`
  - `PATCH /manutencoes/{id}/concluir`
  - `PATCH /manutencoes/{id}/cancelar`
- `GET /health` está faltando.

**api-entregas (:3000), 18 faltando:**
- Entregas (9):
  - `GET /entregas/stats`
  - `GET /entregas/{id}`
  - `GET /entregas/pedido/{numero}`
  - `POST /entregas`
  - `PUT /entregas/{id}`
  - `PATCH /entregas/{id}/status`
  - `PATCH /entregas/{id}/atribuir`
  - `DELETE /entregas/{id}`, que só faz soft-cancel.
  - Os filtros `?veiculo=`, `?status=` e `?motorista_id=` do `GET /entregas` também não estão no openapi.
- Rastreamento (4):
  - `GET /rastreamento/{entregaId}`
  - `GET /rastreamento/{entregaId}/ultimo`
  - `GET /rastreamento/pedido/{numero}`
  - `POST /rastreamento/{entregaId}`
- Frotas, o proxy (4):
  - `GET /frotas/veiculos/disponiveis`
  - `GET /frotas/veiculos/{placa}`
  - `GET /frotas/veiculos/{placa}/disponibilidade`
  - `GET /frotas/motoristas/{id}`
- `GET /health` está faltando.

**api-notificacoes (:5000), 8 faltando:**
- `GET /notificacoes`, com os filtros `tipo`, `status`, `canal` e `servicoOrigem`.
- `GET /notificacoes/{id}`
- `POST /notificacoes`
- `POST /notificacoes/{id}/reenviar`
- `GET /notificacoes/stats`
- `GET /notificacoes/templates`
- `POST /notificacoes/processar`
- `GET /health`

**Item do TODO que não existe:** o openapi lista `/api/motoristas/{id}/disponibilidade`, mas esse endpoint não está no backend. O mais próximo é `/api/frotas/veiculos/{placa}/disponibilidade` na api-entregas.

## Próximo passo (pendente de decisão)

Reescrever `shared-types` com as interfaces reais, incluindo `Manutencao`, `Rastreamento` e as de notificação, e regenerar o `openapi.yaml`.

Decisão em aberto: mapear snake_case para camelCase num adapter ou manter o formato do backend. Isso muda o impacto em `painel-admin` e `rastreamento`, que consomem esses types.
