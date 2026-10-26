# Mapa de Dívida Técnica — RotaLog

Data da análise: 2026-10-20
Escopo: 5 repositórios em `rotalog/` (`rotalog-workspace`, `rotalog-api-frotas`, `rotalog-api-entregas`, `rotalog-api-notificacoes`, `rotalog-frontend`).
Método: leitura do código-fonte, manifests, configs, scripts SQL e `docker-compose.yml`; `npm audit` nos dois projetos Node. Nada foi executado além disso (sem build, sem testes, sem `mvn dependency-check` / `dotnet list package --vulnerable`).

> Contexto: o próprio `CLAUDE_CONTEXT.md` descreve o sistema como legado com dívida **intencional** para fins didáticos. Muitos itens abaixo já estão marcados com `FIXME`/`TODO` no código. Este mapa consolida, prioriza e acrescenta achados que **não** estão sinalizados no código (marcados com 🔎).

## Legenda

| Severidade | Significado |
|---|---|
| 🔴 Crítica | Exploração direta, perda de dados ou bug que quebra funcionalidade hoje |
| 🟠 Alta | Risco relevante ou bloqueio de evolução |
| 🟡 Média | Custo recorrente de manutenção |
| 🟢 Baixa | Higiene / polimento |

Categorias: **ARQ** arquitetura · **SEC** segurança · **INC** inconsistência/bug · **DEP** dependências · **INF** infra/operação · **QUA** qualidade/testes.

---

## 1. Resumo executivo

| Projeto | Stack | 🔴 | 🟠 | 🟡 | 🟢 | Principal risco |
|---|---|---|---|---|---|---|
| rotalog-workspace | Docker Compose + PostgreSQL 14 + SQL | 1 | 3 | 2 | 1 | Banco único, usuário admin compartilhado, senha em texto |
| rotalog-api-frotas | Java 11 / Spring Boot 2.7.14 | 1 | 4 | 5 | 1 | Spring Boot EOL, sem segurança, bug de status em `EntregaClient` |
| rotalog-api-entregas | Node / Express 4 / Sequelize 6 | 3 | 4 | 5 | 1 | `.env` versionado, auth fake, endpoint de escrita público |
| rotalog-api-notificacoes | .NET 6 / EF Core 6 | 2 | 4 | 4 | 1 | .NET 6 EOL, violação de bounded context, segredos em `appsettings.json` |
| rotalog-frontend | Nx · Angular 18 + React 18 | 1 | 4 | 5 | 1 | 163 vulnerabilidades no `npm audit`, contratos divergentes do backend |

Temas transversais (detalhados na seção 7): segredos versionados, nenhuma autenticação real, zero testes automatizados, zero CI/CD, zero Dockerfile de serviço, três fontes de verdade para o schema do banco, acoplamento síncrono por HTTP/DB sem resiliência.

### Top 10 por prioridade

1. 🔴 **entregas** — `.env` com credenciais versionado desde o primeiro commit (`SEC-E1`).
2. 🔴 **entregas** — middleware de auth aceita qualquer string ≥ 10 chars e é ignorado se `NODE_ENV != production` (`SEC-E2`).
3. 🔴 **entregas** — `POST /api/rastreamento/:id` sem autenticação, com escrita no banco (`SEC-E3`).
4. 🔴 **notificacoes** — `HealthController` consulta direto `frotas.veiculos` e `entregas.entregas` (`ARQ-N1`).
5. 🔴 **notificacoes** — segredos (SMTP, SMS, DB) em `appsettings.json` versionado (`SEC-N1`).
6. 🔴 **frotas** — `EntregaClient` compara status em minúsculo com status em maiúsculo → nunca detecta entregas ativas (`INC-F1`).
7. 🔴 **workspace** — um único usuário `rotalog_admin` com `GRANT ALL` em todos os schemas e senha `rotalog123` em 5 arquivos (`SEC-W1`).
8. 🔴 **frontend** — 163 vulnerabilidades (11 críticas) no `npm audit` (`DEP-FE1`).
9. 🟠 **todos os backends** — Spring Boot 2.7, .NET 6, Node 18-era: runtimes/frameworks fora de suporte (`DEP-*`).
10. 🟠 **todos** — zero testes e zero CI.

---

## 2. rotalog-workspace (infra / banco)

Arquivos: `docker-compose.yml`, `tools/scripts/*.sql`, `CLAUDE_CONTEXT.md`, `README.md`.

### Segurança
- 🔴 **SEC-W1** — Senha `rotalog123` e usuário `rotalog_admin` em texto no `docker-compose.yml:9-10`; mesma credencial repetida em `rotalog-api-entregas/.env`, `rotalog-api-frotas/.../application.properties`, `rotalog-api-notificacoes/appsettings.json` e no fallback de `src/config/database.js`. Rotacionar = editar 5 lugares.
- 🟠 **SEC-W2** — `init-schemas.sql` dá `GRANT ALL PRIVILEGES` nos 3 schemas ao mesmo usuário. Qualquer serviço comprometido lê/escreve os dados dos outros. É o que viabiliza `ARQ-N1`.
- 🟠 **SEC-W3** — Porta `5432` publicada em `0.0.0.0` (`docker-compose.yml:12`) sem restrição a `127.0.0.1`.

### Arquitetura
- 🟠 **ARQ-W1** — Um único banco/instância para 3 serviços. Isolamento só por schema, sem roles dedicadas, sem RLS (TODOs em `init-schemas.sql`).

### Inconsistências
- 🟡 **INC-W1** 🔎 — **Três fontes de verdade para o schema**: scripts em `rotalog-workspace/tools/scripts/`, `V1__Initial_Schema.sql` no repo frotas, `src/config/migration.sql` no repo entregas, `Data/migration.sql` no repo notificacoes. Já divergem (ex.: `diff V1__Initial_Schema.sql 02-migration-frotas.sql` difere em comentários e `SET search_path`; script `08-add-motorista-veiculo-info.sql` só existe no workspace).
- 🟡 **INC-W2** 🔎 — Docs desatualizados: `CLAUDE_CONTEXT.md` cita Angular 14, React 17, Redux, Node 18 e org GitHub `Charlinho`; o código tem Angular 18, React 18.3, nenhum Redux e o remote é `alura-cursos`. Há também `README.md` com alteração não commitada.

### Infra
- 🟠 **INF-W1** — Compose só tem o Postgres. Nenhum serviço da aplicação roda em container; sem health checks, sem limites de recurso, sem backup (todos TODO no próprio arquivo). `como rodar o projeto.md` exige 4 runtimes locais (JDK+Maven, Node, .NET SDK, Node para frontend).
- 🟡 **INF-W2** 🔎 — Scripts em `docker-entrypoint-initdb.d` só rodam com volume vazio. Mudança de schema exige `docker-compose down -v` (perde dados) — documentado como procedimento normal. Sem ferramenta de migração versionada compartilhada.
- 🟡 **INF-W3** — `version: '3.8'` é obsoleto no Compose v2 (gera warning). `postgres:14` sai de suporte upstream em novembro/2026.
- 🟢 **INF-W4** — Imagem sem tag de digest, sem `restart` policy.

---

## 3. rotalog-api-frotas (Java / Spring Boot)

Arquivos-chave: `pom.xml`, `application.properties`, `controller/*`, `service/*`, `domain/*`, `repository/*`.

### Arquitetura
- 🟠 **ARQ-F1** — `Veiculo` usa `@Inheritance(TABLE_PER_CLASS)` sem subclasses (`domain/Veiculo.java:27`): estratégia que gera `UNION` em queries polimórficas, desnecessária e danosa se subclasses surgirem.
- 🟠 **ARQ-F2** — Serviços acoplados diretamente à infra: `VeiculoService`, `MotoristaService`, `ManutencaoService` chamam `NotificacaoClient` dentro do fluxo de negócio, síncrono e bloqueante. Falha/latência do .NET degrada o cadastro.
- 🟡 **ARQ-F3** — Entidades retornadas direto nos controllers (sem DTO de resposta); `obterEstatisticasFreita()` retorna `String` JSON (método com typo).
- 🟡 **ARQ-F4** — Sem `@ControllerAdvice`: o mesmo bloco `try/catch (RuntimeException)` + `HashMap` está copiado ~20 vezes nos 3 controllers. `RuntimeException` genérico mapeado ora para 404, ora para 400.
- 🟡 **ARQ-F5** — Sem `@Transactional`. `ManutencaoService.iniciar/concluir/cancelar` salvam `Veiculo` e `Manutencao` em duas operações independentes: falha entre elas deixa veículo e manutenção inconsistentes.
- 🟡 **ARQ-F6** — `@Autowired` em campo (testabilidade ruim) em todos os serviços/controllers; `RestTemplate` criado com `new` em 3 classes, sem timeout.
- 🟢 **ARQ-F7** 🔎 — `EntregaClient` não é usado em lugar nenhum (`grep` só encontra a própria classe). Código morto, e ainda com o bug `INC-F1`.

### Segurança
- 🔴 **SEC-F1** — Nenhuma camada de segurança: sem Spring Security, sem auth, CORS `allowedOrigins("*")` (`config/CorsConfig.java`). Qualquer cliente cria/desativa veículos e motoristas.
- 🟠 **SEC-F2** — Credenciais do banco em `application.properties:6-7` (senha em texto).
- 🟠 **SEC-F3** — `logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE` + `show-sql=true` + `org.springframework.web=DEBUG`: parâmetros SQL (CNH, dados de motoristas) vão para o log. Não há profile separando dev de prod.
- 🟡 **SEC-F4** — DTOs sem Bean Validation (`@Valid` ausente, `spring-boot-starter-validation` está no pom mas não usado). `PATCH /manutencoes/{id}/concluir` converte `body.get("custoFinal").toString()` em `BigDecimal` sem validar → `NumberFormatException` vira 400 genérico.
- 🟡 **SEC-F5** — Notificações de `CNH_VENCIDA` disparadas em loop dentro de um `GET` (`MotoristaService.listarComCnhVencida`): endpoint de leitura com efeito colateral e amplificação de chamadas HTTP.

### Inconsistências
- 🔴 **INC-F1** 🔎 — `EntregaClient.veiculoTemEntregasAtivas` compara `"em_transito"` / `"pendente"` (minúsculo) mas a API de entregas grava `EM_TRANSITO`, `PENDENTE`, `ATRIBUIDA` em maiúsculo (`api-entregas/routes/entregas.js:707`). A comparação nunca é verdadeira; também ignora `ATRIBUIDA`. Hoje inerte por ser código morto, mas quebra a regra de negócio no momento em que for usado.
- 🟡 **INC-F2** — Status de `Veiculo`, `Motorista`, `Manutencao` são `String` livre (`ATIVO`, `INATIVO`, `MANUTENCAO`, `PENDENTE`…), sem enum nem `CHECK` no banco. Transições sem validação: `iniciarManutencao` só loga warning se não estiver `PENDENTE`.
- 🟡 **INC-F3** — `atualizarVeiculo` permite reduzir quilometragem (só `log.warn`); `atualizarQuilometragem` só imprime `System.out`. Mistura `System.out`, `log` em pt/en.
- 🟡 **INC-F4** — Datas de criação/atualização setadas à mão em cada método; sem `@PrePersist/@PreUpdate`. Sem `equals/hashCode`.
- 🟡 **INC-F5** 🔎 — Flyway está no pom (8.5.1) mas desabilitado (`spring.flyway.enabled=false`); histórico de commits mostra 4 correções sucessivas de Flyway. Os arquivos `V1`/`V2` ficam como documentação morta e divergem dos scripts do workspace (`INC-W1`).
- 🟡 **INC-F6** 🔎 — URLs dos outros serviços fixas em constantes Java (`"http://localhost:3000"`, `"http://localhost:5000"`) apesar de `application.properties` já ter as chaves comentadas. `HealthCheckController` também fixa as URLs.

### Dependências
- 🟠 **DEP-F1** — Spring Boot **2.7.14** (set/2023): fim do suporte open-source em 2023. Sem patches de segurança gratuitos. Caminho: 3.x (Jakarta `javax.persistence` → `jakarta.persistence`, Java 17+).
- 🟠 **DEP-F2** — `maven.compiler.source/target=11`; o histórico cita Java 21 (`fix(build): update Lombok … Java 21`). Ambiente de build e target divergem.
- 🟡 **DEP-F3** — Versões fixadas por fora do BOM: `postgresql 42.5.1` (sobrescreve o gerenciado pelo Boot, desatualizado), `flyway-core 8.5.1` (2022), `lombok 1.18.34` duplicado no `<dependency>` e no `annotationProcessorPaths`. Plugins Maven antigos (`maven-compiler 3.8.0`, `surefire 2.22.1`).
- 🟢 **DEP-F4** — `spring-boot-starter-logging` declarado explicitamente (já vem transitivo).

### Infra / Qualidade
- 🟠 **INF-F1** — Sem Actuator; health check manual que chama os outros serviços com URLs fixas e engole exceções.
- 🟠 **QUA-F1** — Nenhum teste (`src/test` não existe), apesar de `spring-boot-starter-test` no pom.
- 🟡 **QUA-F2** — Paginação ausente em todos os `findAll`; query nativa onde derived query bastaria (`VeiculoRepository`).

---

## 4. rotalog-api-entregas (Node.js / Express)

Arquivos-chave: `src/index.js`, `src/middleware/*`, `src/routes/*`, `src/services/*`, `src/models/*`, `.env`, `.gitignore`, `package.json`.

### Segurança
- 🔴 **SEC-E1** — `.env` **versionado** desde o primeiro commit (`fd9ec6f`) com `DB_PASSWORD` e `JWT_SECRET`. O `.gitignore` tem a linha `.env` comentada de propósito. O histórico git mantém o segredo mesmo que o arquivo seja removido — exige rotação.
- 🔴 **SEC-E2** — `middleware/auth.js`: (a) bypass total se `NODE_ENV !== 'production'`; (b) em produção só confere que o token tem ≥ 10 caracteres, **não valida assinatura nem expiração**, e injeta `req.user = {role:'admin'}`. `jsonwebtoken` nem está nas dependências. O `JWT_SECRET` é lido e nunca usado.
- 🔴 **SEC-E3** — `/api/rastreamento` é montado **sem** `authMiddleware` (`index.js:77`), inclusive `POST /:entregaId` (escreve no banco). Endpoint público de escrita, sem rate limiting, sem validação de coordenadas.
- 🟠 **SEC-E4** — CORS `*` implementado à mão; sem `helmet`, sem rate limiting, sem limite de tamanho no `express.json()`.
- 🟠 **SEC-E5** — Vazamento de informação: `errorHandler` devolve `err.stack` fora de produção; rotas devolvem `detalhes: error.message`; `errorHandler` loga `req.body` completo.
- 🟡 **SEC-E6** 🔎 — `GET /api/entregas?veiculo=...` e `?motorista_id=` passam `req.query` direto para o `where` do Sequelize. Com `qs` aninhado (`?status[$ne]=x`) um cliente pode injetar operadores do Sequelize; sem whitelist de tipo.
- 🟡 **SEC-E7** — `unhandledRejection` apenas loga; `uncaughtException` encerra sem drenar conexões. `testConnection()` engole o erro e o servidor sobe sem banco.

### Arquitetura
- 🟠 **ARQ-E1** — Lógica de negócio nas rotas (criação de pedido, cálculo de distância, máquina de estados) — sem camada de service/repository. `routes/entregas.js` mistura async/await, `.then()` e raw SQL.
- 🟠 **ARQ-E2** — `routes/frotas.js` é proxy fino para api-frotas, com `http` nativo e callbacks, sem timeout/retry/circuit breaker; falha upstream vira 502 com `err.message` exposto. `/api/frotas/*` duplica o que o frontend poderia chamar direto ou via gateway.
- 🟡 **ARQ-E3** — Dados denormalizados (`motorista_nome`, `veiculo_modelo`) copiados do frotas sem estratégia de sincronização; `PATCH /atribuir` aceita esses valores do cliente sem verificar existência do veículo/motorista nem disponibilidade (comentado como FIXME, mas o service `frotasService.verificarDisponibilidadeVeiculo` existe e não é chamado).
- 🟡 **ARQ-E4** 🔎 — `services/notificacaoService.js` nunca é importado: o serviço de entregas **não envia nenhuma notificação**, apesar de o README/arquitetura prever. Código morto.
- 🟡 **ARQ-E5** — Cálculo de distância (`sqrt(dLat²+dLng²)*111`) e tempo estimado (2 min/km) fictícios, persistidos como dado real.

### Inconsistências / bugs
- 🟠 **INC-E1** — Sem máquina de estados: `PATCH /status` permite `ENTREGUE → PENDENTE`; `DELETE` cancela entrega já entregue. Não há `Rastreamento` + `Entrega` em transação (duas escritas sem `sequelize.transaction`).
- 🟡 **INC-E2** — `numero_pedido` gerado com `Date.now()` + `Math.random()*1000`: colisão possível sob concorrência (constraint `unique` transforma em 500).
- 🟡 **INC-E3** — Filtro por motorista: backend lê `?motorista_id=`, o frontend (`getEntregasPorMotorista`) envia `?motorista=` → filtro ignorado silenciosamente.
- 🟡 **INC-E4** — Status `STRING(20)` sem enum; `Rastreamento.evento` idem; sem FK no banco entre `rastreamentos.entrega_id` e `entregas.id` (assumido só no Sequelize).
- 🟡 **INC-E5** 🔎 — Mistura de logs `console.log` com `sequelize logging: console.log` (todas as queries no stdout) sem níveis.

### Dependências
`npm audit --package-lock-only`: **16 vulnerabilidades (1 crítica, 10 altas, 4 moderadas, 1 baixa)**.
- 🟠 **DEP-E1** — Crítica em `proxy-addr` (transitiva de `express@4`); alta direta em `nodemon`; `uuid` via `sequelize`. Atualizar `express` 4.x para última 4.x resolve parte; migrar para 5.x é o caminho de longo prazo.
- 🟠 **DEP-E2** — `package-lock.json` está no `.gitignore` e **não** versionado → builds não reproduzíveis; os ranges `^` resolvem diferente a cada `npm install`.
- 🟡 **DEP-E3** — Sem campo `engines`; `CLAUDE_CONTEXT.md` fala em Node 18 (EOL abr/2025). Sem `.nvmrc`.
- 🟡 **DEP-E4** — `sequelize@6` e `nodemon@2` defasados; `"test": "echo Error: no test specified"`.

### Qualidade
- 🟠 **QUA-E1** — Zero testes. Sem ESLint/Prettier. `pg-hstore` instalado sem uso aparente.
- 🟢 **QUA-E2** — Sem paginação em `GET /api/entregas` (inclui `rastreamentos` em cada item).

---

## 5. rotalog-api-notificacoes (.NET 6)

Arquivos-chave: `Program.cs`, `Controllers/*`, `Services/NotificacaoService.cs`, `appsettings.json`, `api-notificacoes.csproj`.

### Segurança
- 🔴 **SEC-N1** — `appsettings.json` versionado com `Password=rotalog123` na connection string, `SenderPassword`, `ApiKey` do SMS. Nada vem de variável de ambiente, user-secrets ou vault.
- 🟠 **SEC-N2** — Sem autenticação (`UseAuthorization` sem esquema configurado). `POST /api/notificacoes/processar` e `POST /{id}/reenviar` abertos: qualquer um dispara envio em massa.
- 🟠 **SEC-N3** — `HealthController` expõe `smtpServer`, `smsApiUrl` e contagem de linhas de tabelas de outros serviços. Controllers devolvem `ex.Message` em 500.
- 🟡 **SEC-N4** — CORS `AllowAnyOrigin`; `AllowedHosts: "*"`; Swagger habilitado em qualquer ambiente (`app.UseSwagger()` sem `IsDevelopment()`).
- 🟡 **SEC-N5** — `ReenviarNotificacao` aceita `NovoDestinatario` do cliente e permite reenviar mensagens já enviadas e sem checar `MaxTentativas` → vetor de spam/relay.

### Arquitetura
- 🔴 **ARQ-N1** — **Violação de bounded context**: `HealthController` abre `NpgsqlConnection` próprio, troca `SearchPath=notificacoes` por `frotas`/`entregas` via `string.Replace` e executa `SELECT COUNT(*)` nas tabelas dos outros serviços. Acopla o serviço ao schema alheio e depende de `SEC-W2`.
- 🟠 **ARQ-N2** — Clean Architecture abandonada: `MediatR` 11 registrado (`AddMediatR(typeof(Program))`) mas nenhum handler existe; pacote morto.
- 🟠 **ARQ-N3** — `NotificacaoService` é *god class* concreta (CRUD, templates, envio, retry, estatísticas), sem interface, registrada como classe concreta; impossível mockar.
- 🟠 **ARQ-N4** — Envio **síncrono** dentro da requisição HTTP (`CriarNotificacao → TentarEnviar`), enquanto o chamador (frotas/entregas) também espera: latência encadeada. Retry via endpoint `processar` manual, sem job agendado, sem fila, sem backoff, sem dead-letter.
- 🟡 **ARQ-N5** — `EnsureCreated()` no startup em vez de EF Migrations; erro de banco só gera `Console.WriteLine` e a aplicação continua de pé.
- 🟡 **ARQ-N6** 🔎 — Envio de e-mail/SMS é **simulado** (`Task.Delay` + `new Random().Next(10)==0` para falhar). Nenhuma notificação real é enviada; `EmailSettings`/`SmsSettings` não têm efeito.

### Inconsistências
- 🟡 **INC-N1** — Status/canal como strings (`"PENDENTE"`, `"ENVIADO"`, `"FALHA"`, `"email"`) sem enum; `ToUpper()/ToLower()` espalhado nos filtros.
- 🟡 **INC-N2** — `ListarNotificacoes` e `ObterEstatisticas` carregam a tabela inteira em memória (`ToListAsync()` + `Count` em LINQ-to-objects) — não escala.
- 🟡 **INC-N3** — `appsettings.json` define `ExternalServices:ApiFrotasUrl/ApiEntregasUrl` que nada lê; chave `"TODO"` dentro do JSON de configuração.
- 🟡 **INC-N4** 🔎 — Pasta `obj/` **versionada** (5 arquivos rastreados) — artefatos de build no repositório.
- 🟡 **INC-N5** — `new Random()` por chamada e `Task.Delay` em caminho de produção; `[HttpGet("/api/health")]` com rota absoluta dentro de controller com `[Route("api/[controller]")]` (duas formas de roteamento).

### Dependências
- 🟠 **DEP-N1** — **.NET 6 (net6.0) fora de suporte desde nov/2024.** Migrar para .NET 8 LTS (suporte até nov/2026) ou 10 LTS.
- 🟠 **DEP-N2** — `Npgsql.EntityFrameworkCore.PostgreSQL 6.0.8`, `Microsoft.EntityFrameworkCore 6.0.12` (versões diferentes entre si), `MediatR 11.1.0` + `MediatR.Extensions.Microsoft.DependencyInjection 11.0.0` (pacote de extensão obsoleto desde MediatR 12), `Swashbuckle 6.5.0`.
- 🟡 **DEP-N3** — Sem `dotnet list package --vulnerable` rodado nesta análise; recomendado como primeiro passo.

### Qualidade / Infra
- 🟠 **QUA-N1** — Nenhum projeto de teste. Sem `FluentValidation`, sem Serilog, sem Polly, sem `IHealthCheck` (todos TODO).
- 🟡 **INF-N1** — Porta 5000 fixa em `launchSettings.json` e impressa por `Console.WriteLine` literal (não reflete a porta real).

---

## 6. rotalog-frontend (Nx · Angular 18 + React 18)

Apps: `painel-admin` (Angular), `rastreamento` (React). Libs: `shared-types`, `ui-components`, `api-contracts` (OpenAPI).

### Arquitetura
- 🟠 **ARQ-FE1** — Duas stacks de UI (Angular + React) no mesmo monorepo sem justificativa nem biblioteca compartilhada efetiva: `shared-types` e `ui-components` (React) não são consumidos pelo Angular; types divergem do backend (`INC-FE1`).
- 🟠 **ARQ-FE2** — Angular usa `fetch()` direto em vez de `HttpClient` (`frotas.service.ts`, `entregas.service.ts`): sem interceptors, sem tratamento central de erro, sem cancelamento, sem testabilidade via `HttpTestingController`. Erros viram `[]`/`null` silenciosamente — a UI mostra "lista vazia" quando o backend está fora.
- 🟡 **ARQ-FE3** — Componentes "tudo-em-um": `veiculos.component.ts` (464 linhas), `dashboard` (349), `motoristas` (251) com template inline, lista/form/detalhe misturados. Sem lazy loading, sem guards (`app.routes.ts`).
- 🟡 **ARQ-FE4** — React: `App` e `TrackingDashboard` são class components com `any`; polling a cada 5 s por componente (`setInterval`) **sem aguardar resposta e sem usar o resultado** (`refreshData` busca a entrega e só atualiza `lastUpdate`).
- 🟡 **ARQ-FE5** 🔎 — Cache manual em `FrotasService` (`veiculosCache`) que nunca expira e só é invalidado por mutações feitas por este cliente.
- 🟢 **ARQ-FE6** — `nx-welcome.component.ts` (scaffold do Nx) ainda no repo.

### Segurança
- 🟠 **SEC-FE1** — Nenhuma autenticação/autorização: sem guards, sem interceptor, sem token. Dependente de `SEC-*` nos backends.
- 🟡 **SEC-FE2** 🔎 — URLs `http://localhost:{3000,8080}` fixas em 4+ arquivos (HTTP, não HTTPS); não há `environment.ts`/variável de build.
- 🟢 **SEC-FE3** — `error.log` versionado na raiz.

### Inconsistências
- 🟠 **INC-FE1** — **Contratos divergentes** (`libs/shared-types/src/index.ts` vs backend):
  - `Veiculo.ano` vs backend `anoFabricacao`; `status: 'ativo'` (minúsculo) vs backend `ATIVO`;
  - `Motorista.numeroCnh/dataVencimentoCnh/email/telefone/ativo` vs backend `cnh`, `vencimentoCnh`, `status` (e sem email/telefone);
  - `Entrega` com campos em inglês (`driver_name`, `origin_address`…) vs backend `motorista_nome`, `origem_endereco`…; falta `ATRIBUIDA` no union de status.
- 🟠 **INC-FE2** 🔎 — Chamadas a endpoints que **não existem** no backend: `DELETE /api/veiculos/{id}` (frotas só tem `PATCH /desativar`) e `GET /api/manutencoes?veiculoId=` (backend usa `/manutencoes/veiculo/{id}`). Falham silenciosamente por `catch`.
- 🟡 **INC-FE3** — `getEntregasPorMotorista` usa `?motorista=` (backend: `motorista_id`) e é código morto.
- 🟡 **INC-FE4** 🔎 — `apps/rastreamento/tsconfig.app.json` com `strict: false`/`noImplicitAny: false` enquanto `tsconfig.base.json` e `painel-admin` usam `strict: true` — padrão de rigor diferente por app, com `any` generalizado.
- 🟡 **INC-FE5** — `libs/api-contracts/openapi.yaml` existe mas não é usado para gerar clientes/tipos; contrato manual duplicado.
- 🟡 **INC-FE6** 🔎 — Doc diz "Redux sem Toolkit"; não há Redux em lugar nenhum — sobra o campo `reduxState: null` no state e comentários TODO.

### Dependências
`npm audit`: **163 vulnerabilidades (11 críticas, 111 altas, 32 moderadas, 9 baixas)**.
- 🔴 **DEP-FE1** — Críticas transitivas: `handlebars`, `koa`, `shell-quote`, `tar`, `proxy-addr`, `websocket-driver`, `vitest/tinypool/piscina`, `@module-federation/dts-plugin`, `@nx/vite`. Majoritariamente em toolchain (dev), mas expõem o ambiente de build/CI e dev server.
- 🟠 **DEP-FE2** — **Angular 18.2** (EOL nov/2025) e **Nx 19.8.4** (diversas majors atrás). `@nrwl/angular` e `@nrwl/react` (escopo antigo, descontinuado em favor de `@nx/*`) coexistem com `@nx/*` na mesma versão.
- 🟠 **DEP-FE3** — Todas as dependências estão em `devDependencies` (até `react`, `react-dom`, `@angular/*`); `dependencies` só tem `leaflet`. Funciona no build do Nx, mas confunde auditorias de produção e ferramentas de deploy.
- 🟡 **DEP-FE4** — `@types/node: 18.16.9` fixo; `prettier ^2.6.2` (v3 existe); `ts-node 10.9.1` fixo; `jest-preset-angular ~14.1` (precisa casar com Angular). Sem `@types/react-leaflet` explícito.
- 🟡 **DEP-FE5** 🔎 — `package-lock.json` com alteração não commitada; `workspaces: ["packages/*"]` aponta para pasta vazia (`.gitkeep`).

### Qualidade / Infra
- 🟠 **QUA-FE1** — Cobertura de testes ≈ 0: um `app.component.spec.ts` e o `example.spec.ts` do Playwright (scaffold). Nenhum teste de serviços/componentes/libs.
- 🟡 **QUA-FE2** — Budgets de componente em 2–4 kB (`anyComponentStyle`) com CSS/templates inline grandes: build de produção provavelmente falha ou emite warnings.
- 🟡 **INF-FE1** — Sem CI (lint/test/build), sem Dockerfile/nginx para servir o build, sem `.env` por ambiente, `AGENTS.md`/`opencode.json`/`CLAUDE.md` coexistindo (3 convenções de agente).

---

## 7. Dívidas transversais

| ID | Tema | Detalhe | Projetos |
|---|---|---|---|
| **X-1** | Segredos em texto | Mesma senha em 5 lugares; `.env` e `appsettings.json` versionados | workspace, entregas, frotas, notificacoes |
| **X-2** | Sem autenticação real | Nenhum serviço valida identidade; CORS `*` em todos | todos |
| **X-3** | Zero testes | Nenhum teste unitário/integração/contrato em backend; ~0 no frontend | todos |
| **X-4** | Zero CI/CD e containers | Nenhum `.github/`, nenhum Dockerfile, nenhum pipeline | todos |
| **X-5** | Schema em 3 fontes | Scripts workspace × migrations de serviço × `ddl-auto`/`EnsureCreated`/`sync` | todos |
| **X-6** | Resiliência | HTTP síncrono sem timeout/retry/circuit breaker entre serviços (frotas→notificacoes, entregas→frotas, notificacoes→DB alheio) | frotas, entregas, notificacoes |
| **X-7** | Observabilidade | Só `console.log`/`System.out`/`Console.WriteLine`; sem logs estruturados, métricas, tracing, correlation-id; health checks manuais | todos |
| **X-8** | Config hardcoded | URLs de serviços e portas fixas no código | todos |
| **X-9** | Modelagem por strings | Status como `String` sem enum/`CHECK`: `ATIVO`, `EM_TRANSITO`, `PENDENTE`… com caixas diferentes entre serviços (`INC-F1`, `INC-FE1`) | todos |
| **X-10** | Runtimes fora de suporte | Spring Boot 2.7, .NET 6, Angular 18, Postgres 14 (nov/2026), Node 18-era | todos |
| **X-11** | Contratos entre serviços não versionados | OpenAPI existe só no frontend e não é fonte; chamadas entre serviços sem schema | todos |
| **X-12** | Documentação desatualizada | `CLAUDE_CONTEXT.md` e READMEs descrevem versões/orgs que não batem com o código | workspace |

---

## 8. Plano de ataque sugerido

### Fase 0 — Contenção (1–2 dias)
1. Rotacionar `rotalog_admin`/`JWT_SECRET`/chaves SMTP-SMS (`SEC-E1`, `SEC-N1`, `SEC-W1`). Remover `.env` do tracking e reativar a linha no `.gitignore`; considerar reescrever histórico.
2. Fechar `POST /api/rastreamento/:id` (`SEC-E3`) e remover o bypass por `NODE_ENV` (`SEC-E2`).
3. Publicar a porta 5432 só em `127.0.0.1` (`SEC-W3`).
4. Remover `obj/` do git em notificacoes (`INC-N4`); versionar `package-lock.json` em entregas (`DEP-E2`).

### Fase 1 — Fundação (1–2 semanas)
5. Secrets via variáveis de ambiente / user-secrets / `.env.example` em todos os serviços (`X-1`, `X-8`).
6. Autenticação real (JWT com validação de assinatura) + CORS restritivo + roles de banco por serviço (`X-2`, `SEC-W2`).
7. Remover `HealthController` → acesso cross-schema; usar `IHealthCheck` e chamadas HTTP aos `/health` (`ARQ-N1`).
8. Escolher **uma** fonte de verdade do schema por serviço (Flyway no frotas, EF Migrations no notificacoes, Sequelize-CLI/Knex no entregas) e tirar do `docker-compose` (`X-5`).
9. Corrigir bugs: `INC-F1` (status em caixa alta), `INC-FE2` (endpoints inexistentes), `INC-E1` (transições + transação), `ARQ-F5`.
10. Dockerfiles + serviços no compose + healthchecks (`INF-W1`).

### Fase 2 — Atualização de plataforma (2–4 semanas)
11. Spring Boot 3.x / Java 21; .NET 8 LTS (ou 10); Node LTS atual + Express 5; Angular 19+/Nx atual (`DEP-*`, `X-10`).
12. `npm audit fix` + remoção dos escopos `@nrwl/*`; mover runtime deps para `dependencies` (`DEP-FE1..FE3`).
13. CI mínima (build + lint + test + audit) por repositório (`X-4`).

### Fase 3 — Qualidade e arquitetura (contínuo)
14. Testes: contratos entre serviços (Pact/OpenAPI), unitários nos services, e2e do painel (`X-3`).
15. `@ControllerAdvice`, Bean Validation, DTOs de resposta, enums (`ARQ-F3/F4`, `X-9`).
16. Desacoplar notificações: fila/outbox em vez de HTTP síncrono (`ARQ-N4`, `ARQ-F2`, `X-6`).
17. Frontend: `HttpClient` + interceptors, `environment.ts`, tipos gerados do OpenAPI, lazy loading, quebrar componentes grandes (`ARQ-FE*`, `INC-FE*`).
18. Observabilidade: logs estruturados, métricas, correlation-id (`X-7`).

---

## 9. Limitações desta análise

- `npm audit` foi executado com acesso à rede; números podem variar com o tempo. Não rodei `mvn org.owasp:dependency-check` nem `dotnet list package --vulnerable` — as vulnerabilidades de Java e .NET estão inferidas pelas versões, não confirmadas por scanner.
- Não executei os serviços, builds nem testes. Afirmações marcadas como bug (`INC-F1`, `INC-FE2`, `SEC-E6`) foram derivadas de leitura de código; vale reproduzir antes de priorizar.
- Não li integralmente os arquivos SQL de seed, `Models/*.cs`, `domain/Motorista`/`Manutencao`, `ui-components`, `MapView.tsx`, `DeliveryList.tsx` nem os componentes Angular (apenas contagem de linhas).
- Datas de fim de suporte (Spring Boot 2.7, .NET 6, Angular 18, Node 18, Postgres 14) vêm do calendário público de cada projeto na data de análise; confirme antes de usar em decisão formal.
