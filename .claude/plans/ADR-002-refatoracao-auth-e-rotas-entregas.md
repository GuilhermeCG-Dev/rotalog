# ADR-002 — Refatoração do `auth.js` e de `routes/entregas.js` (api-entregas)

| Campo | Valor |
|---|---|
| Status | **Proposta** (aguardando avaliação; nada foi implementado) |
| Data | 2026-10-27 |
| Escopo | `rotalog-api-entregas` — `src/middleware/auth.js`, `src/routes/entregas.js` e dependentes diretos (`index.js`, `routes/rastreamento.js`, `middleware/errorHandler.js`) |
| Base | [mapa-divida-tecnica.md](../../plans/mapa-divida-tecnica.md) (seção 4 e seção 7) |
| ADR de referência | [ADR-001](../../plans/ADR-001-refatoracao-veiculo-service.md) (formato e estilo) |
| Convenções aplicadas | `.claude/agents/node-entregas.md`: `routes -> services -> models`; routes só validam entrada e delegam; nunca `console.log` de debug; Jest ao criar testes; sem `sync` automático |

---

## 1. Contexto

Dois arquivos do `rotalog-api-entregas` concentram a maior parte da dívida de segurança e de responsabilidade do serviço:

- `middleware/auth.js` (51 linhas) — **não autentica ninguém**. Existe só para parecer que há autenticação.
- `routes/entregas.js` (365 linhas) — deveria só mapear rota → service, mas contém regra de negócio, SQL cru, `console.*` (12 ocorrências), 37 `FIXME` e ~76 linhas de comentário (≈ 21% do arquivo).

Itens do mapa de dívida técnica que este ADR cobre, total ou parcialmente:

`SEC-E1`, `SEC-E2`, `SEC-E3`, `SEC-E4`, `SEC-E5`, `SEC-E6`, `ARQ-E1`, `ARQ-E3`, `ARQ-E5`, `INC-E1`, `INC-E2`, `INC-E4`, `INC-E5`, `QUA-E1`, `QUA-E2`, `X-2`, `X-3`, `X-7`, `X-9`.

Estado de testes hoje: **nenhum** (`npm test` é placeholder). Não há `jsonwebtoken`, validador de schema, logger nem `helmet` nas dependências; `src/services/` só tem `frotasService.js` (usado por `routes/frotas.js`) e `notificacaoService.js` (nunca importado). Não existe `entregaService`.

Uso real do `authMiddleware` (`grep` em `src/`): apenas `index.js:75` (`/api/entregas`) e `index.js:77` (`/api/frotas`). Ninguém lê `req.user`, então o objeto "Admin" fake não é consumido por nenhuma regra hoje.

---

## 2. Problemas encontrados

Severidade segue a legenda do mapa: 🔴 crítica · 🟠 alta · 🟡 média · 🟢 baixa. Itens marcados 🔎 não estão sinalizados com `FIXME` no código nem constam no mapa.

### 2.1 `middleware/auth.js` — vulnerabilidades

| ID | Sev. | Problema | Local |
|---|---|---|---|
| AU-01 | 🔴 | **Bypass total fora de produção**: `NODE_ENV !== 'production'` chama `next()` sem checar nada. Basta o ambiente subir sem `NODE_ENV=production` (ou com typo, ex.: `prod`) para a API ficar aberta. É *fail-open*. Mapa: `SEC-E2`. | l.16-19 |
| AU-02 | 🔴 | **Token não é validado**: em "produção" só confere `Bearer <algo>` com `length >= 10`. `Bearer aaaaaaaaaa` passa. Sem verificação de assinatura, `exp`, `iss`/`aud`. Mapa: `SEC-E2`. | l.28-43 |
| AU-03 | 🔴 | **Identidade forjada**: toda requisição aceita recebe `req.user = { id: 1, nome: 'Admin', role: 'admin' }`. Qualquer cliente vira admin. | l.46 |
| AU-04 | 🔴 | **Segredo hardcoded como fallback**: `process.env.JWT_SECRET \|\| 'super-secret-key-…'`. Se a variável faltar, o serviço sobe silenciosamente com segredo público (está no repositório). Mapa: `SEC-E1`, `X-1`. | l.12 |
| AU-05 | 🔴 | **`.env` versionado** (confirmado: `git ls-files .env` retorna o arquivo; `.gitignore` tem `# .env` comentado de propósito) com `JWT_SECRET` e `DB_PASSWORD`. Segredo permanece no histórico git mesmo após remoção: exige **rotação**. Mapa: `SEC-E1`. | `.env`, `.gitignore:45` |
| AU-06 | 🟠 | `JWT_SECRET` é lido e **nunca usado** para verificar nada; `jsonwebtoken` não está no `package.json`. Segurança inexistente, não apenas fraca. | `package.json` |
| AU-07 | 🟠 🔎 | **Comparação `parts[0] !== 'Bearer'` case-sensitive**: `bearer x` (válido pela RFC 7235) é rejeitado, e o esquema não é normalizado. Detalhe pequeno, mas indica que o parsing foi copiado sem revisão. | l.29 |
| AU-08 | 🟠 🔎 | **Logs de auth no caminho quente**: `console.log('[AUTH] Bypass…')` roda **a cada requisição** em dev; `'Token aceito (sem validação real)'` roda a cada requisição em "prod". Ruído, sem correlação, sem nível. Mapa: `X-7`. | l.17, 43 |
| AU-09 | 🟡 | Mensagens de erro diferenciam "não fornecido" / "mal formatado" / "inválido" — facilita enumeração/diagnóstico por atacante. Padrão: resposta única `401` + `WWW-Authenticate: Bearer`. | l.24, 30, 37 |
| AU-10 | 🟡 | Sem refresh token, sem revogação, sem rotação de chave (`kid`). Dependem de **decisão de produto** (ver §6): este serviço pode simplesmente *validar* tokens emitidos por outro. | cabeçalho |

### 2.2 `middleware/auth.js` — arquitetura e responsabilidade

| ID | Sev. | Problema | Detalhe |
|---|---|---|---|
| AU-11 | 🟠 | **Sem autorização (RBAC)**. Não existe `requireRole(...)`. `PATCH /status`, `PATCH /atribuir` e `DELETE` têm `FIXME: Sem autorização` e são acessíveis a qualquer autenticado (na prática, a qualquer um). | `entregas.js:233` |
| AU-12 | 🟠 | **Config lida em tempo de importação** (`const JWT_SECRET = …` no topo do módulo) e `NODE_ENV` lido a cada request. Não há validação de configuração no boot: faltar segredo deveria **impedir a subida**, não degradar. | l.12, 16 |
| AU-13 | 🟠 | **Middleware faz três coisas**: lê config, decide política de ambiente (bypass) e "autentica". Sem fronteira para trocar o provedor (JWT local, JWKS, gateway). | classe de problema |
| AU-14 | 🔴 | **Cobertura de rota incompleta**: `/api/rastreamento` **não** passa por `authMiddleware` (`index.js:76`), inclusive `POST /:entregaId`, que **escreve** no banco. Mapa: `SEC-E3`. Fora do arquivo `auth.js`, mas consequência direta do seu uso opcional/manual. | `index.js:76` |
| AU-15 | 🟠 🔎 | **Proteção por montagem manual**: cada `app.use` decide se coloca o middleware. Esquecer um (como já aconteceu) deixa rota pública. O padrão seguro é *deny by default* com lista explícita de rotas públicas. | `index.js:75-77` |
| AU-16 | 🟡 | Sem rate limiting, sem `helmet`, CORS `*` manual (`SEC-E4`) — não é do `auth.js`, mas sem eles o endpoint de auth/escrita não tem defesa contra força bruta nem abuso. | `index.js` |
| AU-17 | 🟡 | Comentários: **17 de 51 linhas** (33%) são comentário; o cabeçalho (9 linhas) descreve dívida, não comportamento; "Copied from StackOverflow (2020)" é dado de proveniência sem licença/autoria. | todo o arquivo |
| AU-18 | 🟢 | Estilo: `var` e `function` de 2020 em arquivo novo para o padrão do projeto (`camelCase`, `const/let`). | todo o arquivo |

### 2.3 `routes/entregas.js` — SQL e acesso a dados na rota

| ID | Sev. | Problema | Local |
|---|---|---|---|
| RT-01 | 🟠 | **SQL cru dentro da rota**: `GET /stats` define a query de agregação e a executa via `sequelize.query(…, { type: sequelize.constructor.QueryTypes.SELECT })`. Hardcode do schema `entregas.entregas` (quebra se `DB_SCHEMA` mudar). Mapa: `ARQ-E1`. | l.65-91 |
| RT-02 | 🟡 | O mesmo resultado sai de `Entrega.findAll({ attributes: [status, [fn('COUNT')…]], group: ['status'] })`; a rota importa `sequelize` **só** para isso. | l.14, 77 |
| RT-03 | 🟡 🔎 | **Contagem devolvida como string**: `COUNT(*)`/`AVG` voltam como `string` do `pg`; o handler faz `parseInt(s.total)` só no total e devolve `por_status` com `total`, `distancia_media`, `peso_medio` como **strings**. Contrato inconsistente dentro da mesma resposta. | l.79-84 |
| RT-04 | 🟠 | **Acesso a model direto na rota**: 10 handlers chamam `Entrega`/`Rastreamento` (`findAll`, `findByPk`, `create`, `save`). Sem camada de service nem repository. Mapa: `ARQ-E1`. | arquivo todo |
| RT-05 | 🟠 🔎 | **Filtros sem whitelist de tipo** (`req.query.veiculo`, `motorista_id`): com `qs` aninhado (`?motorista_id[$ne]=0`) o valor vira objeto e entra no `where` do Sequelize. Risco de injeção de operador. `status` usa `.toUpperCase()` e **lança `TypeError`** se for array (`?status=a&status=b`) → 500. Mapa: `SEC-E6`. | l.27-39 |
| RT-06 | 🟡 | `GET /` sem paginação, com `include` de `rastreamentos` (`limit: 5` hardcoded). Mapa: `QUA-E2`. | l.41-50 |
| RT-07 | 🟡 🔎 | `limit` dentro de `include` hasMany do Sequelize 6 pode gerar subquery/`LATERAL` pesada ou ser ignorado conforme dialeto: validar comportamento real antes de manter. | l.44-49 |

### 2.4 `routes/entregas.js` — lógica de negócio na rota

| ID | Sev. | Problema | Local |
|---|---|---|---|
| RT-08 | 🟠 | **Criação de pedido na rota**: gera número, calcula distância, calcula tempo, monta entidade e grava evento. Mapa: `ARQ-E1`. | l.142-200 |
| RT-09 | 🟡 | `numero_pedido = 'PED-' + Date.now() + '-' + random(1000)`: colisão possível sob concorrência (constraint `unique` → 500). Mapa: `INC-E2`. Usar UUID/sequence. | l.153 |
| RT-10 | 🟠 | **Distância "fake" persistida como dado real**: `sqrt(dLat²+dLng²)*111` ignora `cos(lat)`; erro cresce com a latitude. `tempo_estimado = km*2` hardcoded. Mapa: `ARQ-E5`. | l.156-165 |
| RT-11 | 🟠 🔎 | **Bug de coordenada zero**: `if (origem_lat && origem_lng && …)` trata `0` como ausente (equador/meridiano de Greenwich) e `origem_lat \|\| null` grava `null` para `0`. Também `peso_kg \|\| null` zera peso `0`. Strings numéricas passam e são somadas por `Math.abs(destino - origem)` com coerção implícita. | l.157, 171-175 |
| RT-12 | 🟠 | **Sem máquina de estados**: `PATCH /status` permite `ENTREGUE → PENDENTE`; `DELETE` cancela entrega já `ENTREGUE`; `PATCH /atribuir` reatribui entrega `EM_TRANSITO`/`ENTREGUE`/`CANCELADA` e **volta o status para `ATRIBUIDA`**. Mapa: `INC-E1`, `X-9`. | l.250, 344, 308 |
| RT-13 | 🟠 | **Duas escritas sem transação**: `entrega.save()` seguido de `Rastreamento.create()` em 4 fluxos (criar, status, atribuir, cancelar). Falha no 2º deixa entrega alterada sem evento de auditoria. Mapa: `INC-E1`. | l.167-190, 262-270, 311-319, 348-357 |
| RT-14 | 🟠 | **`atribuir` não valida nada no api-frotas**; `motorista_nome` e `veiculo_modelo` vêm do cliente (dado denormalizado forjável). `frotasService.verificarDisponibilidadeVeiculo` já existe e **não é chamado**. Mapa: `ARQ-E3`. | l.287-328 |
| RT-15 | 🟡 | `PUT /:id` aceita `peso_kg` sem tipo/limite (negativo, string, `NaN`) e `origem/destino_endereco` sem tamanho; `if (origem_endereco)` ignora string vazia silenciosamente. Também permite `PUT` em entrega `ENTREGUE`/`CANCELADA`. | l.205-227 |
| RT-16 | 🟡 🔎 | **Rotas ordenadas por acaso**: `GET /pedido/:numeroPedido` está **depois** de `GET /:id`. Hoje funciona porque `/pedido/X` tem 2 segmentos e `/:id` casa 1; `/stats` só funciona porque foi declarada antes de `/:id`. Frágil a reordenação. | l.65, 96, 120 |
| RT-17 | 🟡 🔎 | `:id` não é validado (`/api/entregas/abc` vai ao banco e pode virar erro de cast 500). | todas as rotas `/:id` |
| RT-18 | 🟡 | Datas `data_criacao`/`data_atualizacao` setadas à mão em 5 lugares (`new Date()` repetido ~12×); falta hook do model ou `timestamps`. | várias |
| RT-19 | 🟡 🔎 | **Respostas devolvem o model inteiro** (`res.json(entrega)`): campos internos e denormalizados vão ao cliente sem DTO. | todos |
| RT-20 | 🟡 | **Vazamento de detalhe de erro**: `POST /` devolve `detalhes: error.message` (mensagem do Sequelize/pg). Mapa: `SEC-E5`. | l.198 |
| RT-21 | 🟡 | **Tratamento de erro repetido** em 9 blocos `try/catch` / `.catch()`; sem `asyncHandler` e sem erros tipados — qualquer erro (inclusive 404 lógico) segue o mesmo caminho 500. | arquivo todo |
| RT-22 | 🟠 | **Mistura de 3 estilos**: `async/await` (6 handlers), `.then()` aninhado (3: `/stats`, `/pedido/:n`, `DELETE`) e `function(){}` vs arrow. Callback hell em `DELETE` (3 níveis). Mapa: `ARQ-E1`. | l.65, 120, 336 |

### 2.5 `routes/entregas.js` — logs

| ID | Sev. | Problema | Local |
|---|---|---|---|
| RT-23 | 🟡 | **12 chamadas `console.*`** (7 `console.error`, 3 `console.log` de sucesso/debug, + as do `index.js`). Violam a convenção do projeto ("nunca deixar console.log de debug"). Mapa: `INC-E5`, `X-7`. | l.54, 88, 112, 130, 192, 196, 224, 272, 276, 321, 325, 360 |
| RT-24 | 🟡 | `console.error(…, error.message)` **perde o stack trace**: diagnóstico fica difícil; `errorHandler` global loga `req.body` completo (`SEC-E5`). | todos |
| RT-25 | 🟡 🔎 | `console.log` de `numero_pedido`, placa e status em cada operação: dado de negócio no stdout sem nível nem `requestId`, e duplica o log de request de `index.js:47`. | l.192, 272, 321 |
| RT-26 | 🟢 | Mensagens em pt-BR misturadas com mensagens em inglês no `index.js`; sem padrão. | — |

### 2.6 `routes/entregas.js` — comentários

| ID | Sev. | Problema | Detalhe |
|---|---|---|---|
| RT-27 | 🟢 | **~76 de 365 linhas são comentário (≈ 21%)** e **37 `FIXME`**. Parte repete o código ("Filtro por status", "Registrar evento", "Filtro por motorista"). | todo o arquivo |
| RT-28 | 🟢 | Blocos JSDoc de rota que só dizem o verbo + path (`GET /api/entregas/:id - Buscar entrega por ID`) duplicam a definição da rota. O contrato HTTP deve morar no OpenAPI (`libs/api-contracts`), não em comentário. Mapa: `INC-FE5`. | l.16-20, 59-64, … |
| RT-29 | 🟢 | `FIXME`s já **desatualizados** em relação a este ADR ("Callback style", "Validação inline", "Sem autorização") — viram backlog/teste, não comentário. | l.121, 138, 233, 337 |
| RT-30 | 🟢 | Cabeçalho com `FIXME: 60% callbacks` — métrica sem fonte. | l.4 |

### 2.7 Testes

| ID | Sev. | Problema |
|---|---|---|
| TS-01 | 🟠 | **Cobertura 0%**. `npm test` falha de propósito. Sem rede de segurança para refatorar. Mapa: `QUA-E1`, `X-3`. |
| TS-02 | 🟡 | Impossível testar rota sem banco: lógica acoplada ao `Entrega` do Sequelize e à instância global `sequelize`. Services com dependências injetáveis resolvem. |
| TS-03 | 🟡 | `requests.http` na raiz é o único artefato de teste manual; não é executável em CI. |

---

## 3. Decisão

Refatorar em **duas frentes ordenadas** (auth primeiro, porque a rota depende de `req.user` para RBAC), sem alterar contrato HTTP exceto onde marcado em §3.9.

### 3.1 Autenticação: validar JWT de verdade

- Adicionar `jsonwebtoken` (única dependência nova obrigatória).
- `authMiddleware` passa a: extrair `Bearer` (case-insensitive), `jwt.verify(token, secret, { algorithms: ['HS256'], issuer, audience })`, popular `req.user = { id, nome, role }` **a partir das claims**, e responder sempre `401` genérico com `WWW-Authenticate: Bearer` em qualquer falha (AU-01 a AU-03, AU-07, AU-09).
- **Remover o bypass por `NODE_ENV`** (AU-01). Para desenvolvimento, usar token gerado por script (`npm run token:dev`) ou segredo de dev no `.env.example`; **nunca** pular a verificação por ambiente.
- **Remover o fallback** do segredo (AU-04). Ausência de `JWT_SECRET` ⇒ falha no boot.
- Arquivo novo `src/middleware/authorize.js`: `requireRole(...roles)` (AU-11). Papéis iniciais sugeridos: `admin`, `operador`, `motorista` (ver §6).

### 3.2 Configuração validada no boot

- Novo `src/config/env.js`: lê, valida e **congela** as variáveis (`JWT_SECRET` com mínimo de 32 caracteres, `JWT_ISSUER`, `JWT_AUDIENCE`, `API_FROTAS_URL`, `CORS_ORIGINS`, `NODE_ENV`). Falha rápida com mensagem clara (AU-12).
- `auth.js`, `index.js` e `services/*` passam a importar `config`, não `process.env`.
- Criar `.env.example` sem valores reais. **Remover `.env` do tracking** (`git rm --cached .env`) e reativar `.env` no `.gitignore` (AU-05). Reescrever histórico é decisão do time (§6).
- **Rotacionar** `JWT_SECRET` e `DB_PASSWORD` (ação operacional, fora do código).

### 3.3 Proteção *deny by default*

- Em `index.js`, aplicar `authMiddleware` **globalmente** logo após `/api/health`, com lista explícita de rotas públicas (AU-14, AU-15):

| Rota | Acesso |
|---|---|
| `GET /api/health` | público |
| `GET /api/rastreamento/pedido/:numeroPedido` | público (tracking do cliente final), **somente leitura**, com rate limit |
| `GET /api/rastreamento/:entregaId`, `/:entregaId/ultimo` | autenticado (ou público — §6) |
| `POST /api/rastreamento/:entregaId` | **autenticado**, papel `motorista` ou `admin` |
| `/api/entregas/*` | autenticado; escrita com `requireRole` |
| `/api/frotas/*` | autenticado |

- Adicionar `helmet`, `express-rate-limit` e `cors` (já é dependência, hoje não usado) com `CORS_ORIGINS` configurável, e `express.json({ limit: '100kb' })` (AU-16; mapa `SEC-E4`).

### 3.4 Camada de service para entregas (resolve RT-01, RT-04, RT-08, RT-22)

Meta: `routes/entregas.js` com ~60 linhas, **zero** `console`, **zero** SQL, **zero** acesso direto a model.

| Arquivo novo | Responsabilidade | Recebe de |
|---|---|---|
| `routes/entregas.js` (reduzido) | Mapear verbo+path → middleware de validação → controller/service; nada mais. | — |
| `services/entregaService.js` | Casos de uso: `listar`, `buscarPorId`, `buscarPorPedido`, `criar`, `atualizar`, `atribuir`, `cancelar`, `alterarStatus`. Orquestra transação + evento. | l.22-363 |
| `services/entregaStatus.js` | Enum `EntregaStatus` + tabela de transições + `podeTransitar(de, para)` (RT-12). | l.245-253, 344 |
| `services/distanciaService.js` | `calcularDistanciaKm(origem, destino)` com **Haversine**; `estimarTempoMinutos(km)` com velocidade média configurável (RT-10). | l.156-165 |
| `services/numeroPedido.js` | Gera `PED-<yyyymmdd>-<uuid curto>` (RT-09). | l.153 |
| `repositories/entregaRepository.js` | Todas as queries Sequelize, inclusive `contarPorStatus()` com `fn('COUNT')` (RT-01, RT-02, RT-03). | l.22-57, 65-91 |
| `validators/entregaValidator.js` | Schemas de body/query/params (ver §3.5). | validações inline |
| `middleware/validate.js` | `validate({ body, query, params })` genérico; devolve `400` com lista de campos. | — |
| `middleware/asyncHandler.js` | Encaminha rejeições para o `errorHandler` (RT-21). | 9 `try/catch` |
| `errors/` | `AppError` + `NotFoundError`, `ValidationError`, `ConflictError` (transição inválida), `UpstreamError`. | respostas ad-hoc |
| `config/logger.js` | Logger estruturado (`pino`) com nível por ambiente e `requestId` (RT-23 a RT-25). | `console.*` |

Regras de desenho:

- Services recebem dependências por parâmetro/fábrica (`criarEntregaService({ entregaRepo, rastreamentoRepo, frotasService, logger, sequelize })`), para permitir teste sem banco (TS-02).
- **Transação**: criar/alterar status/atribuir/cancelar executam `sequelize.transaction()` envolvendo `Entrega` + `Rastreamento` (RT-13). `Rastreamento` ganha helper `registrarEvento(entregaId, evento, descricao, { transaction })`.
- `routes/frotas.js` e `frotasService.js` continuam como estão, **exceto** que `frotasService` vira promise-based (ver §3.7).

### 3.5 Validação de entrada (RT-05, RT-11, RT-15, RT-17)

- Biblioteca: **Joi** ou **Zod** (escolha em §6). Whitelist de chaves (`stripUnknown`), tipos coagidos de forma explícita.
- `params.id`: inteiro positivo. `query.status`: um valor do enum, string única (rejeita array/objeto). `query.veiculo`: string `^[A-Z0-9-]{7}$`. `query.motorista_id`: inteiro. Qualquer objeto aninhado ⇒ `400` (fecha RT-05).
- `POST`: `origem_endereco`/`destino_endereco` string 3-255; lat ∈ [-90, 90], lng ∈ [-180, 180] e **`0` é válido** (RT-11); `peso_kg` número ≥ 0 e ≤ limite configurável; coordenadas só aceitas **em par** (ou ambas ausentes).
- `PUT`: mesmos limites; proibido em entrega `ENTREGUE`/`CANCELADA` (`409`).
- Paginação em `GET /`: `?page=1&limit=20` (máx. 100); resposta mantém array por padrão enquanto `page` não for enviado (compatibilidade, §3.9).

### 3.6 Máquina de estados (RT-12)

```
PENDENTE ──► ATRIBUIDA ──► EM_TRANSITO ──► ENTREGUE
   │             │              │
   └─────────────┴──────────────┴──────► CANCELADA
```

| De \ Para | PENDENTE | ATRIBUIDA | EM_TRANSITO | ENTREGUE | CANCELADA |
|---|---|---|---|---|---|
| PENDENTE | — | ✔ | ✘ | ✘ | ✔ |
| ATRIBUIDA | ✘ | ✔ (reatribuição) | ✔ | ✘ | ✔ |
| EM_TRANSITO | ✘ | ✘ | — | ✔ | ✔ |
| ENTREGUE | ✘ | ✘ | ✘ | — | ✘ |
| CANCELADA | ✘ | ✘ | ✘ | ✘ | — |

- `ENTREGUE` e `CANCELADA` são terminais. `data_coleta`/`data_entrega` setados pela service dentro da transição.
- Transição inválida ⇒ `ConflictError` ⇒ `409` com `{ erro, de, para }`.
- `PATCH /atribuir` só em `PENDENTE`/`ATRIBUIDA`; reatribuir `EM_TRANSITO` fica **bloqueado**.
- `DELETE` continua sendo cancelamento lógico (soft delete) e passa a validar a tabela.

### 3.7 Integração com api-frotas na atribuição (RT-14)

- `frotasService` passa a retornar **promises** (`util.promisify` ou `fetch` nativo do Node 18+) com **timeout** configurável (`FROTAS_TIMEOUT_MS`, padrão 3000) — escopo mínimo, sem circuit breaker (`X-6` continua).
- `atribuir` chama `verificarDisponibilidadeVeiculo(placa)` e `buscarMotoristaPorId(id)` e **preenche** `veiculo_modelo`/`motorista_nome` com o dado retornado, **ignorando** o que o cliente enviar (fecha o vetor de dado forjado, `ARQ-E3`).
- Falha do upstream ⇒ `502`/`503` sem `err.message` do upstream no corpo.
- Alinhado com o bug `INC-F1` do mapa: comparar status **em caixa alta** (`ATIVO`) — já é o que `frotasService` faz.

### 3.8 Limpeza (comentários, logs, estilo)

- **Comentários (RT-27 a RT-30, AU-17)**: remover todo comentário que repete o código e todos os blocos JSDoc `VERBO /path - descrição`. Manter comentário **só** onde há regra não óbvia (tabela de transição, fórmula Haversine). `FIXME` resolvido por este ADR é apagado; o restante vira item de backlog.
- **Logs (RT-23 a RT-26)**: substituir `console.*` por `logger` (`info` para eventos de negócio, `warn` para 4xx esperados, `error` com `err` completo para 5xx). Logar **ids**, nunca corpo da requisição. `errorHandler`: parar de devolver `stack` e `err.message` de erros não-`AppError`; logar com `requestId`.
- **Estilo (AU-18, RT-22)**: `const/let`, arrow functions, `async/await` em tudo.
- **ESLint** (`eslint:recommended` + `no-console: error` + `no-var`) para impedir regressão da convenção do projeto.

### 3.9 Mudanças de contrato visíveis (precisam de aval)

| Mudança | Antes | Depois |
|---|---|---|
| Qualquer rota sem token, em dev | acessível | `401` |
| `POST /api/rastreamento/:id` sem token | `201` | `401` |
| Token inválido/expirado/forjado | aceito (≥10 chars) | `401` |
| Mensagens de erro de auth | 3 textos distintos | texto único |
| Transição de status inválida | aceita | `409` |
| `DELETE` em entrega `ENTREGUE` | cancela | `409` |
| `PATCH /atribuir` | aceita `motorista_nome`/`veiculo_modelo` do cliente | ignora; usa dado do api-frotas |
| `PATCH /atribuir` com veículo inexistente/indisponível | aceita | `404`/`409` |
| `GET /stats` | números como string em `por_status` | números como `number` |
| `GET /?status=` inválido ou aninhado | 500 / filtro injetável | `400` |
| `POST /` com coordenada `0` | tratada como ausente | aceita |
| `numero_pedido` | `PED-<ms>-<0..999>` | `PED-<yyyymmdd>-<id curto>` |
| Erro `POST /` | `{ error, detalhes }` | `{ error }` |
| Erros inesperados | `stack` fora de produção | nunca |

Consumidores a conferir: `painel-admin` e `rastreamento` (frontend, ver `INC-FE1`/`INC-FE3`), `api-frotas` (`EntregaClient`, morto) e `requests.http`. O frontend **não envia token** hoje (`SEC-FE1`): ativar auth real quebra o front até ele enviar `Authorization` — **este ADR cria uma dependência com o frontend** (§6, questão 2).

---

## 4. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Manter bypass em dev (`if NODE_ENV !== 'production'`) | É a causa raiz de AU-01. Um erro de config em produção vira porta aberta. Dev usa token gerado, não ausência de verificação. |
| Passport.js / `express-jwt` | Útil, mas acrescenta abstração para um caso simples (verificar HS256 + claims). `jsonwebtoken` direto é menos código e mais fácil de testar. Revisar se surgir OAuth/JWKS. |
| API Key estática em vez de JWT | Simples, mas sem identidade nem papéis; não dá `req.user` para RBAC nem auditoria. |
| Delegar auth a um API Gateway e remover o middleware | Direção correta no longo prazo (`X-2`), mas não existe gateway hoje; o serviço continuaria exposto. Pode ser adotado depois sem mudar as rotas. |
| Só mover o SQL do `/stats` para fora e deixar o resto | Resolve o item mais visível e deixa RT-08, RT-12, RT-13, RT-14 (os de maior risco). |
| Usar Sequelize `Model` hooks para regras de negócio | Esconde regra em *side-effects*; dificulta teste e transação explícita. |
| Controller separado da service (`routes -> controllers -> services`) | Convenção do projeto é `routes -> services -> models`. A rota reduzida já faz o papel de controller (validar + delegar). Evita uma camada extra. |
| Migrar para NestJS/TypeScript | Fora de escopo; é um ADR próprio (`DEP-E1`, Express 5). |
| Reescrever tudo de uma vez | Sem testes de caracterização, risco alto; ver §5. |

---

## 5. Plano de execução sugerido

Cada passo termina com `npm test` e `npm run lint` verdes.

1. **Contenção (sem refatorar)**: `git rm --cached .env`, `.gitignore`, `.env.example`; **rotacionar** segredos; `POST /api/rastreamento/:id` atrás de `authMiddleware` (`SEC-E3`). Pode sair como PR separado, **antes** de qualquer outra coisa.
2. **Rede de segurança**: instalar Jest + Supertest; testes de **caracterização** das rotas de `entregas.js` com repositório/model mockado, incluindo os bugs conhecidos (RT-11, RT-12) marcados `test.skip` com o comportamento desejado. Configurar `--coverage` (relatório apenas).
3. **Config + logger**: `config/env.js`, `config/logger.js`; trocar `console.*` em `index.js`, `errorHandler.js`, `routes/*`; ESLint com `no-console`.
4. **Auth**: `jsonwebtoken`, novo `authMiddleware`, `requireRole`, helper de token de dev; deny-by-default em `index.js`; `helmet`, rate limit, CORS por env. Testes unitários do middleware (token ausente, mal formado, assinatura errada, expirado, `iss`/`aud` errados, papel insuficiente, case de `Bearer`).
5. **Erros + validação**: `AppError`, `asyncHandler`, `validate`, schemas; `errorHandler` sem `stack`.
6. **Extrair** `entregaRepository`, `entregaStatus`, `distanciaService`, `numeroPedido`; testes de cada um.
7. **Extrair** `entregaService` com transação; reduzir `routes/entregas.js`; reabilitar testes `skip`.
8. **Integração frotas** na atribuição (timeout + validação + preenchimento server-side).
9. **Limpeza final**: comentários, JSDoc, `FIXME` resolvidos, `var` → `const`.
10. **Gate**: Jest `coverageThreshold` (≥ 80% linha no escopo) no `npm test`.

Estimativa grosseira: 4 a 6 dias de uma pessoa; o passo 1 sozinho leva horas e deve ser feito primeiro.

---

## 6. Questões em aberto (decisão do time)

1. **Quem emite o JWT?** Este serviço só **valida** (proposto) ou também faz login? Se não há serviço de identidade, quem gera o token que o front enviará?
2. **Frontend**: o `painel-admin` e o `rastreamento` precisam enviar `Authorization`. Há prazo para essa mudança? Se não, definir janela com flag de transição — **sem** reintroduzir bypass por ambiente.
3. **Algoritmo e chave**: HS256 com segredo compartilhado (proposto, simples) ou RS256/JWKS (melhor se mais serviços validarem o mesmo token)?
4. **Papéis**: `admin`/`operador`/`motorista` são suficientes? Quem pode `DELETE`, `PATCH /status`, `POST /rastreamento`?
5. **Rastreamento público**: `GET /api/rastreamento/pedido/:numeroPedido` continua público (cliente final)? Se sim, `numero_pedido` precisa ser **imprevisível** (UUID) para não permitir enumeração — o formato atual (`ms + 0..999`) é adivinhável.
6. **Reescrever histórico git** para remover `.env`? Impacta todos os clones. Se não, a rotação dos segredos é obrigatória e suficiente.
7. **Validador**: Joi ou Zod? (Zod só vale a pena se houver TypeScript no horizonte.)
8. **Logger**: `pino` (proposto) ou `winston`? Formato JSON em prod e `pino-pretty` em dev?
9. **Reatribuição** de entrega `ATRIBUIDA` (trocar motorista) é permitida? Proposto: sim, em `PENDENTE`/`ATRIBUIDA`.
10. **Paginação**: quebrar contrato (sempre `{ itens, total, page }`) ou manter array enquanto `page` não for enviado (proposto)?
11. **Velocidade média** para `tempo_estimado`: manter 30 km/h (2 min/km) como padrão configurável ou integrar API de rotas depois?
12. **Cobertura**: 80% em linha (proposto) ou 90% como no ADR-001? Escopo: `services/`, `middleware/`, `validators/`, `repositories/`?
13. **Notificação**: `notificacaoService` nunca é chamado (`ARQ-E4`). Entra neste trabalho (notificar ao atribuir/entregar) ou fica separado? (Proposta: separado.)
14. **Versão do Node/Express**: fixar `engines` e `.nvmrc` agora; migração para Express 5 (`DEP-E1`) é ADR à parte.

---

## 7. Consequências

**Positivas**
- A API passa a **rejeitar** requisições não autenticadas; `req.user` reflete a identidade real.
- Fecha o bypass por ambiente e o segredo público no código; falta de config impede o boot em vez de degradar.
- `routes/entregas.js` cai de 365 para ~60 linhas, sem SQL, sem `console`, sem regra de negócio.
- Transições de estado e transações garantem consistência entrega ↔ rastreamento.
- Dados denormalizados (`motorista_nome`, `veiculo_modelo`) passam a vir do api-frotas, não do cliente.
- Cobertura de testes sai de 0% para ≥ 80% no escopo, com gate.
- Fecha ou reduz no mapa: `SEC-E1`, `SEC-E2`, `SEC-E3`, `SEC-E4` (parcial), `SEC-E5`, `SEC-E6`, `ARQ-E1`, `ARQ-E3`, `ARQ-E5` (parcial), `INC-E1`, `INC-E2`, `INC-E5`, `QUA-E1` (entregas), `X-3` (entregas), `X-7` (entregas).

**Negativas / riscos**
- **Quebra o frontend** até que ele envie `Authorization` (§3.9, §6.2). Precisa de coordenação; o ADR não cobre o lado front.
- Muitos arquivos novos (≈ 14) para um serviço pequeno: mais navegação; mitigado por nomes por responsabilidade.
- Novas dependências: `jsonwebtoken`, `helmet`, `express-rate-limit`, `pino`, validador, Jest/Supertest, ESLint. Cada uma entra no `npm audit` (hoje 16 vulnerabilidades, `DEP-E1`); `package-lock.json` precisa ser versionado (`DEP-E2`) — o `.gitignore` atual o ignora.
- Rotacionar `JWT_SECRET`/`DB_PASSWORD` exige atualizar também `docker-compose.yml`, `api-frotas` e `api-notificacoes` (`SEC-W1`, `X-1`): senha é compartilhada entre 5 lugares.
- Máquina de estados rejeita fluxos que hoje "funcionam" (ex.: voltar `ENTREGUE → PENDENTE` para corrigir erro operacional). Pode exigir um endpoint administrativo de correção — ver §6.
- `Rastreamento` sem FK no banco (`INC-E4`): a transação protege a consistência da aplicação, mas não impede órfãos criados por fora.
- Dados existentes com status fora dos 5 valores (coluna sem `CHECK`): conferir antes com `SELECT DISTINCT status FROM entregas.entregas;`.
- Testes de caracterização do passo 2 travam bugs por um período curto; precisam ser invertidos no passo 7.

**Não resolve (continua no mapa)**: `SEC-E7` (graceful shutdown), `ARQ-E2` (proxy de frotas sem circuit breaker/retry), `ARQ-E4` (notificação nunca chamada), `INC-E3` (nome do filtro no front), `INC-E4` (enum/FK no banco), `DEP-E1`/`DEP-E3`/`DEP-E4` (versões), `X-5` (schema em 3 fontes), `X-6` (resiliência), `X-4` (CI/CD), `SEC-W1`/`SEC-W2` (credencial única do banco).

---

## 8. Referências

- [mapa-divida-tecnica.md](../../plans/mapa-divida-tecnica.md) — seções 4 (entregas) e 7 (transversais)
- [ADR-001-refatoracao-veiculo-service.md](../../plans/ADR-001-refatoracao-veiculo-service.md) — formato de referência
- `rotalog-api-entregas/src/middleware/auth.js`
- `rotalog-api-entregas/src/routes/entregas.js`
- `rotalog-api-entregas/src/routes/rastreamento.js`
- `rotalog-api-entregas/src/routes/frotas.js`
- `rotalog-api-entregas/src/index.js`
- `rotalog-api-entregas/src/middleware/errorHandler.js`
- `rotalog-api-entregas/src/services/frotasService.js`
- `rotalog-api-entregas/.env`, `.gitignore`, `package.json`
- `.claude/agents/node-entregas.md`

*Limitações desta análise: baseada em leitura de código; nada foi executado, instalado ou testado. Contagens de linha/comentário/`console.*` referem-se ao estado atual da branch `feat/adding-adr`. Não li `models/Entrega.js`, `models/Rastreamento.js`, `routes/frotas.js` nem `notificacaoService.js` por completo, apenas os pontos que tocam estes dois arquivos. Comportamento de `limit` em `include` (RT-07) e coerção de `qs` aninhado (RT-05) vêm do conhecimento do Sequelize/Express e devem ser reproduzidos antes de priorizar.*
