# ADR-001 — Refatoração da `VeiculoService`

| Campo | Valor |
|---|---|
| Status | **Proposta** (aguardando avaliação; nada foi implementado) |
| Data | 2026-10-27 |
| Escopo | `rotalog-api-frotas` — `com.rotalog.service.VeiculoService` e dependentes diretos |
| Base | [mapa-divida-tecnica.md](mapa-divida-tecnica.md) (seção 3 e seção 7) |
| Convenções aplicadas | `.claude/agents/java-frotas.md`: SLF4J (nunca `System.out.println`), JUnit 5 + Mockito, `controller -> service -> repository` |

---

## 1. Contexto

`VeiculoService` (339 linhas) concentra CRUD, validação de entrada, regras de negócio (manutenção, custo, transição de status), estatísticas, montagem de JSON e integração com a api-notificacoes. É o exemplo mais claro, no módulo de frotas, de vários itens já catalogados no mapa de dívida técnica:

`ARQ-F2`, `ARQ-F3`, `ARQ-F4`, `ARQ-F5`, `ARQ-F6`, `INC-F2`, `INC-F3`, `INC-F4`, `INC-F6`, `QUA-F1`, `QUA-F2`, `X-3`, `X-6`, `X-7`, `X-9`.

Estado de testes hoje: existe apenas `VeiculoControllerTest` (2 casos, com `VeiculoService` mockado). **Nenhum teste exercita o código da service.** O `pom.xml` não tem JaCoCo, então a cobertura nem é medida. (O mapa dizia que `src/test` não existia — já existe, mas é mínimo.)

Dependentes da service e do estado de `Veiculo`:

- `VeiculoController` — chama 10 métodos e captura `RuntimeException` em todos.
- `ManutencaoService` — grava `veiculo.setStatus("MANUTENCAO" | "ATIVO")` e compara strings (linhas 115, 142, 174-175).
- `VeiculoRepository` — `findByStatus(String)` e JPQL com literal `'ATIVO'`.
- `VeiculoControllerTest` — usa `setStatus("ATIVO")`.

---

## 2. Problemas encontrados

Severidade segue a legenda do mapa: 🔴 crítica · 🟠 alta · 🟡 média · 🟢 baixa. Itens marcados 🔎 não estão sinalizados com `FIXME` no código nem constam no mapa.

### 2.1 Design e responsabilidades

| ID | Sev. | Problema | Local |
|---|---|---|---|
| VS-01 | 🟠 | **God class**: 16 métodos públicos em 6 responsabilidades (consulta, cadastro/validação, quilometragem, ciclo de vida/status, política de manutenção, estatísticas) + integração de notificação. Mapa: `ARQ-F2`. | classe inteira |
| VS-02 | 🟡 | Injeção por campo com `@Autowired`; impede `new VeiculoService(mock, mock)` em teste unitário puro. Mapa: `ARQ-F6`. | l.27-31 |
| VS-03 | 🟠 | Notificação síncrona e bloqueante dentro do fluxo de negócio, repetida em 5 métodos com o mesmo `try/catch (Exception)`. Falha/latência do .NET degrada o cadastro. Mapa: `ARQ-F2`, `X-6`. | l.107-117, 174-183, 219-227, 273-281 |
| VS-04 | 🟡 | Destinatário `"gestor@rotalog.com"` e tipos de notificação (`"NOVO_VEICULO"`, `"ALERTA_MANUTENCAO"`, `"MANUTENCAO_AGENDADA"`, `"VEICULO_DESATIVADO"`) hardcoded 4×. | idem |
| VS-05 | 🟡 | `@Transactional` ausente. Leitura+escrita (`buscarPorId` → `save`) sem fronteira transacional; notificação disparada antes de qualquer commit garantido. Mapa: `ARQ-F5`. | todos os métodos de escrita |
| VS-06 | 🟡 | Entidade `Veiculo` devolvida direto para o controller (sem DTO de resposta). Mapa: `ARQ-F3`. | retornos |
| VS-07 | 🟡 | Datas `dataCadastro`/`dataAtualizacao` setadas à mão em 6 lugares; falta `@PrePersist/@PreUpdate`. Mapa: `INC-F4`. | l.100-101, 146, 168, 267, 292 |

### 2.2 Exceções genéricas

| ID | Sev. | Problema | Local |
|---|---|---|---|
| VS-08 | 🟠 | **9 `throw new RuntimeException(...)`** para erros de natureza diferente (não encontrado, validação, duplicidade, status inválido, km negativa). O chamador só distingue pela *mensagem*. | l.49, 57, 70, 74, 80, 85, 90, 160, 196 |
| VS-09 | 🟠 | Consequência no controller: `catch (RuntimeException)` mapeia **tudo** para um único status por endpoint. `desativar`/`reativar` devolvem 404 mesmo para erro inesperado (ex.: `DataAccessException`, `NullPointerException`); `registrar` devolve 400 para falha de banco. Mapa: `ARQ-F4`. | `VeiculoController` |
| VS-10 | 🟡 | `catch (Exception e)` amplo nas notificações: engole inclusive erros de programação. Mensagem logada sem stack trace (`e.getMessage()`), o que dificulta diagnóstico. | 5 pontos |

### 2.3 Bugs e riscos de comportamento

| ID | Sev. | Problema | Detalhe |
|---|---|---|---|
| VS-11 | 🔴 🔎 | **Duplicidade de placa burlável por caixa**: `findByPlaca(placa)` consulta o valor *cru*, mas o save grava `placa.toUpperCase()`. `"abc1234"` não encontra `"ABC1234"` existente → passa pela checagem e cai na constraint `unique` do banco (vira 500/`DataIntegrityViolationException` capturada como `RuntimeException` → 400 sem mensagem útil). | l.78 vs 95 |
| VS-12 | 🟠 🔎 | **Corrida check-then-insert**: mesmo com a caixa corrigida, `findByPlaca` + `save` não é atômico; duas requisições simultâneas passam na checagem. Precisa tratar `DataIntegrityViolationException` → `PlacaDuplicadaException`. | l.78-103 |
| VS-13 | 🟠 🔎 | **`NullPointerException` por unboxing**: `veiculo.getQuilometragem()` (coluna nullable, `Long`) é comparado com `<` em `atualizarVeiculo` (l.138) e `atualizarQuilometragem` (l.163). `novaQuilometragem` nulo também estoura em `novaQuilometragem < 0` (l.159) e `calcularCustoManutencao` (l.241). Com NPE, o controller devolve 400 com `erro: null`. | l.138, 159, 163, 241 |
| VS-14 | 🟠 🔎 | `obterVeiculosPorStatus(null)` → NPE em `status.equals(...)`. Comparação é case-sensitive: `/veiculos/status/ativo` retorna 400 "Status inválido". | l.195 |
| VS-15 | 🟠 | Regra de quilometragem **inconsistente** entre dois métodos: `atualizarVeiculo` só loga `warn` ao reduzir km; `atualizarQuilometragem` só imprime `System.out` e **também aceita a redução**. Nenhum dos dois protege o odômetro. Mapa: `INC-F3`. | l.138-143, 163-165 |
| VS-16 | 🟠 🔎 | `reativarVeiculo` não valida estado de origem: veículo em `MANUTENCAO` vira `ATIVO` sem concluir a manutenção (que continua `EM_ANDAMENTO` em `ManutencaoService`). `desativarVeiculo` também não bloqueia veículo em manutenção nem com entregas ativas (`EntregaClient` existe, é código morto e tem o bug `INC-F1`). Mapa: `INC-F2`. | l.264-296 |
| VS-17 | 🟡 🔎 | `desativarVeiculo` é não idempotente: desativar um veículo já `INATIVO` regrava e **reenvia notificação**. `reativarVeiculo` não notifica — assimetria sem justificativa. | l.264-296 |
| VS-18 | 🟡 | `atualizarVeiculo` não valida `anoFabricacao` (a regra 1900–2100 só existe em `registrarVeiculo`) e aceita `quilometragem` negativa. Validação duplicada/ausente por não haver validador único. | l.128-148 |
| VS-19 | 🟡 🔎 | Validação de placa só checa `length == 7`: aceita `"!!!!!!!"`. Não normaliza `trim()`. `"  ABC123"` passa como 7 caracteres. Nenhum formato (antigo `AAA9999` ou Mercosul `AAA9A99`). Ano máximo `2100` é número mágico. | l.69-91 |
| VS-20 | 🟡 🔎 | `precisaDeManutencao(veiculoId)` chama `buscarPorId` de novo (2ª ida ao banco) logo depois de `atualizarQuilometragem` já ter o veículo. Também dispara notificação a **cada** atualização acima de 50 000 km (sem deduplicação) — spam de alerta. | l.173, 249-256 |
| VS-21 | 🟡 🔎 | `calcularCustoManutencao` usa `Double` para dinheiro (erro de arredondamento) e ignora o parâmetro `modelo`. Retorno `Double`/`Boolean` boxed sem necessidade. | l.236-242, 249 |
| VS-22 | 🟡 | `obterEstatisticasFreita` (typo): 4 queries, 3 delas carregam a lista inteira só para `.size()`; não atômico (contagens de instantes diferentes); `total` pode não bater com a soma dos status. Retorna JSON montado com `String.format` (sem escape, sem tipo). Mapa: `ARQ-F3`, `QUA-F2`. | l.304-316 |
| VS-23 | 🟡 | `listarTodos` e `obterVeiculosPorStatus` sem paginação. Mapa: `QUA-F2`. | l.39, 194 |

### 2.4 Código morto e variáveis não utilizadas

| ID | Sev. | Item | Local |
|---|---|---|---|
| VS-24 | 🟢 | `intervaloQuilometragem` (10000L) e `intervaloMeses` (3) declaradas e **nunca usadas**. | l.212-213 |
| VS-25 | 🟢 | Parâmetro `modelo` de `calcularCustoManutencao` nunca lido. | l.236 |
| VS-26 | 🟡 | `agendarManutencaoPreventiva` **não agenda nada**: só loga e notifica. `quilometragemLimite` é usado apenas na mensagem. Nome promete mais do que faz. | l.208-228 |
| VS-27 | 🟢 | `sincronizarComSistemaExterno` é stub (log + `System.out`), sem chamador. | l.324-332 |
| VS-28 | 🟢 🔎 | Import `Optional` só existe para a checagem de duplicidade que pode virar `existsByPlaca`. Variável local `existente` poderia ser eliminada. | l.78 |
| VS-29 | 🟡 🔎 | Métodos públicos sem chamador fora da classe: `buscarPorPlaca` (só controller), `agendarManutencaoPreventiva`, `calcularCustoManutencao`, `precisaDeManutencao`, `sincronizarComSistemaExterno` não são expostos por nenhum endpoint — API de service sem uso real. Confirmar antes de remover. | — |

### 2.5 Logs e documentação

| ID | Sev. | Problema | Local |
|---|---|---|---|
| VS-30 | 🟡 | **3 `System.out.println`** (l.116, 164, 327). Violam a convenção do projeto e o `X-7`. Dois deles duplicam um log SLF4J ao lado ou são o único aviso (l.164). | l.116, 164, 327 |
| VS-31 | 🟢 | Logs mistos pt/en e sem padrão ("Listando todos…" com comentário "misturando pt/en"). `log.info` em `listarTodos` roda a cada GET sem valor de diagnóstico. | l.40 |
| VS-32 | 🟢 | **Javadoc em excesso**: 16 blocos de comentário, maioria só repete o nome do método ("Busca veículo por ID", "Reativa veículo") e a outra parte é lista de `FIXME` em formato de Javadoc (inválido como documentação). Cabeçalho da classe de 10 linhas descreve a dívida, não o comportamento. ~120 das 339 linhas são comentário. | todo o arquivo |
| VS-33 | 🟢 | 40+ `FIXME`/`TODO` inline, vários desatualizados em relação ao que o ADR decidir. | todo o arquivo |

### 2.6 Modelagem por string

| ID | Sev. | Problema |
|---|---|---|
| VS-34 | 🟠 | Status `"ATIVO"`, `"INATIVO"`, `"MANUTENCAO"` repetidos como literal na `VeiculoService` (8×), `ManutencaoService` (4×), `VeiculoRepository` (JPQL), `VeiculoControllerTest`. Whitelist manual `!status.equals(...) && ...` em `obterVeiculosPorStatus` precisa ser editada à mão a cada novo status. Mapa: `INC-F2`, `X-9`. |
| VS-35 | 🟡 | Tipos de notificação (`"NOVO_VEICULO"`…) e canal (`"email"`, em `NotificacaoClient`) também são strings soltas; typo vira erro silencioso no consumidor. |

### 2.7 Testes

| ID | Sev. | Problema |
|---|---|---|
| VS-36 | 🟠 | Cobertura da service ≈ **0%**. Os 2 testes existentes mockam a própria service. Mapa: `QUA-F1`, `X-3`. |
| VS-37 | 🟡 | Sem JaCoCo → sem meta mensurável nem *gate* no build. |
| VS-38 | 🟡 | Impossível testar a service isoladamente hoje: injeção por campo + `NotificacaoClient` com `RestTemplate` criado via `new` e URL fixa (um teste que chamasse o caminho de notificação tentaria conexão real em `localhost:5000`). |

---

## 3. Decisão

Refatorar `VeiculoService` em **uma única linha de trabalho**, sem alterar contrato HTTP (exceto onde marcado em §3.8), na ordem da §5. Os 8 pontos pedidos mapeiam para as decisões abaixo.

### 3.1 Dividir responsabilidades (pedidos 1 e 7)

Meta: nenhuma classe acima de ~150 linhas; `VeiculoService` volta a ser fachada de orquestração.

| Classe nova | Responsabilidade | Recebe de |
|---|---|---|
| `VeiculoService` (reduzida) | Consulta, `registrar`, `atualizar`, orquestração. | — |
| `VeiculoValidator` | Normaliza e valida placa, modelo, ano, quilometragem. Única fonte das regras (resolve VS-18, VS-19). | l.69-91, 131-144, 159-161 |
| `VeiculoStatusService` | `desativar`, `reativar`, validação de transição (resolve VS-16, VS-17). | l.264-296 |
| `VeiculoQuilometragemService` | `atualizarQuilometragem`, regra de não-regressão (resolve VS-13, VS-15, VS-20). | l.156-186 |
| `ManutencaoPreventivaPolicy` | `precisaDeManutencao(Veiculo)`, `calcularCusto(...)`; limites e custos lidos de `@ConfigurationProperties` (resolve VS-21 e remove hardcode). | l.208-256 |
| `VeiculoNotificador` | Único ponto que fala com `NotificacaoClient`: tipos via enum, destinatário via propriedade, `try/catch` + log com stack trace, uma vez só (resolve VS-03, VS-04, VS-10, VS-35). | 4 blocos `try/catch` |
| `EstatisticasFrotaService` + DTO `EstatisticasFrotaResponse` | Contagem por `countByStatus` (resolve VS-22). | l.304-316 |

Regras de desenho:

- Injeção por construtor (`@RequiredArgsConstructor` + campos `final`) em todas as classes novas e na `VeiculoService`; some o `@Autowired` de campo (VS-02).
- `@Transactional` nos métodos de escrita; `@Transactional(readOnly = true)` nas consultas (VS-05). Notificação é chamada **após** o commit (ver §3.9, questão em aberto).
- Pacote sugerido: `com.rotalog.service` (classes de serviço/política), `com.rotalog.service.validation` (`VeiculoValidator`), `com.rotalog.exception`, `com.rotalog.domain` (enums).

### 3.2 Exceções específicas (pedido 2)

Hierarquia em `com.rotalog.exception`, todas estendendo uma base `RotalogException extends RuntimeException`:

| Exceção | Substitui | HTTP sugerido |
|---|---|---|
| `VeiculoNaoEncontradoException(id \| placa)` | l.49, 57 | 404 |
| `PlacaInvalidaException` | l.70, 74 (e formato, VS-19) | 400 |
| `PlacaDuplicadaException` | l.80 + `DataIntegrityViolationException` (VS-11/12) | 409 |
| `DadoVeiculoInvalidoException(campo, motivo)` | l.85, 90 | 400 |
| `QuilometragemInvalidaException` | l.160 + regressão de km (VS-15) | 422 |
| `StatusVeiculoInvalidoException` | l.196 | 400 |
| `TransicaoStatusInvalidaException(de, para)` | novo (VS-16) | 409 |

- Mensagens preservadas onde existirem (o `VeiculoControllerTest` assere `"Veículo não encontrado: 99"`).
- Criar `@RestControllerAdvice` (`GlobalExceptionHandler`) para mapear a hierarquia e **remover** os `try/catch (RuntimeException)` do `VeiculoController` (mapa `ARQ-F4`). Mantém o corpo `{"erro": "..."}` para não quebrar o front.
- `catch (Exception)` nas notificações passa a capturar `RestClientException` (ou a exceção específica do client), nunca `Exception`.

### 3.3 Enums no lugar de strings (pedido 6)

- `VeiculoStatus { ATIVO, INATIVO, MANUTENCAO }` em `com.rotalog.domain`, com método `podeTransitarPara(VeiculoStatus)` (tabela de transição única).
- `TipoNotificacao { NOVO_VEICULO, ALERTA_MANUTENCAO, MANUTENCAO_AGENDADA, VEICULO_DESATIVADO }` e, opcionalmente, `CanalNotificacao { EMAIL, SMS }` — mantendo o valor serializado atual (`"email"`).
- `Veiculo.status` → `@Enumerated(EnumType.STRING) VeiculoStatus status`. **Valores no banco não mudam** (já são `ATIVO`/`INATIVO`/`MANUTENCAO`); não exige migration.
- `VeiculoRepository.findByStatus(VeiculoStatus)`, `countByStatus(VeiculoStatus)`; JPQL `'ATIVO'` → parâmetro ou literal de enum.
- `ManutencaoService` precisa ser ajustado (4 pontos) para usar `VeiculoStatus`. Status de `Manutencao` e `Motorista` **ficam fora** deste ADR (ver §6) — recomenda-se um ADR próprio, mas a mesma abordagem se aplica.
- Controller: `@PathVariable VeiculoStatus status` com conversor case-insensitive (resolve VS-14); valor inválido → `StatusVeiculoInvalidoException`.

### 3.4 Limpeza (pedidos 4, 5 e 8)

- **Javadoc (4)**: remover todos os blocos que apenas repetem o nome do método e as listas de `FIXME` dentro de Javadoc. Manter Javadoc curto **apenas** onde há regra não óbvia (ex.: política de não-regressão de km, regra de transição). Comentários `FIXME/TODO` resolvidos por este ADR são apagados; os restantes viram item no backlog, não comentário.
- **`System.out.println` (5)**: remover os 3 (l.116, 164, 327); l.164 vira `log.warn`/exceção conforme decisão de VS-15. Padronizar logs em pt-BR, parametrizados (`{}`), com exceção como último argumento para registrar o stack trace. Remover `log.info` de `listarTodos`.
- **Não utilizados (8)**: remover `intervaloQuilometragem`, `intervaloMeses`, parâmetro `modelo` de `calcularCustoManutencao`, import/variável `existente` (usar `existsByPlaca`), e o stub `sincronizarComSistemaExterno` (VS-24 a VS-28). `agendarManutencaoPreventiva` (VS-26): ver §6, decisão do time.

### 3.5 Correções de comportamento incluídas

Entram porque a refatoração só é segura se estes pontos forem resolvidos junto: VS-11, VS-12, VS-13, VS-14, VS-15, VS-16, VS-17, VS-20. Cada um ganha teste unitário próprio antes da correção (§3.7).

### 3.6 Entidade

- `@PrePersist`/`@PreUpdate` em `Veiculo` para `dataCadastro`/`dataAtualizacao` (VS-07); remover as 6 atribuições manuais da service.
- Fora do escopo: `TABLE_PER_CLASS` (`ARQ-F1`), `equals/hashCode`.

### 3.7 Testes unitários — meta ≥ 90% (pedido 3)

- **Ferramentas**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), sem Spring context. Possível porque a injeção passa a ser por construtor.
- **Escopo medido**: pacotes `com.rotalog.service`, `com.rotalog.service.validation`, `com.rotalog.exception`. Excluir da medição: `Application`, `config`, DTOs/entidades puramente Lombok.
- **JaCoCo** (`jacoco-maven-plugin`): relatório + regra `check` com `LINE >= 0.90` e `BRANCH >= 0.80` no escopo acima, ligada à fase `verify`. Se o mínimo de 90% se aplicar também a *branches*, ajustar a regra — ver §6.
- **Um arquivo de teste por classe**, com `@Nested` por método quando ajudar:

| Teste | Casos mínimos |
|---|---|
| `VeiculoServiceTest` | `buscarPorId` ok / não encontrado; `buscarPorPlaca` ok / não encontrado; `listarTodos`; `registrar` ok (placa em maiúscula, status `ATIVO`, km 0, notifica), duplicada (incl. caixa diferente), corrida (`DataIntegrityViolationException`), falha de notificação **não** impede retorno; `atualizar` campo a campo (null/vazio ignora, ano e km inválidos). |
| `VeiculoValidatorTest` | placa nula/vazia/branca/6/8 chars/caracteres inválidos/minúscula/com espaços/formato antigo e Mercosul; modelo nulo/vazio; ano 1899/1900/2100/2101/nulo; km nula/negativa/zero. |
| `VeiculoStatusServiceTest` | desativar ok; já inativo (idempotente, sem notificar); em manutenção (bloqueia); reativar de `INATIVO`; reativar de `MANUTENCAO` (bloqueia); não encontrado. |
| `VeiculoQuilometragemServiceTest` | atualiza e salva; nula; negativa; regressão; igual; cruza limite → notifica 1×; já acima do limite → política de dedup; `veiculo.getQuilometragem()` nulo. |
| `ManutencaoPreventivaPolicyTest` | limite exato (49 999 / 50 000 / 50 001), km nula; custo com `BigDecimal` (base + km × taxa), km 0, valores de propriedade customizados. |
| `VeiculoNotificadorTest` | monta tipo/destinatário/mensagem corretos por `TipoNotificacao`; `RestClientException` é engolida e logada; exceção não esperada propaga. |
| `EstatisticasFrotaServiceTest` | contagens por status; frota vazia; total = soma. |
| `GlobalExceptionHandlerTest` | cada exceção → status + corpo `{"erro": ...}`. |
| `VeiculoControllerTest` (existente, ampliar) | `@WebMvcTest`: 404, 400, 409, 422, 201, listagem por status case-insensitive. |

- Convenção de nome: `metodoDeveFazerXQuandoY` (já usada em `VeiculoControllerTest`). Dados via helper/builder de teste (`VeiculoFixture`) — evita repetir `new Veiculo()` + 6 setters.
- `LocalDateTime.now()` direto na service dificulta asserts de data; com `@PrePersist` o problema some da service.

### 3.8 Mudanças de contrato visíveis (precisam de aval)

| Mudança | Antes | Depois |
|---|---|---|
| Placa duplicada | 400 | 409 |
| Veículo não encontrado em `PATCH /desativar` e `/reativar` | 404 | 404 (mantido) |
| Erro inesperado em endpoints que hoje devolvem 400/404 | 400/404 + `erro: <msg>` | 500 (via advice) |
| Reduzir quilometragem | aceito | rejeitado (422) |
| `GET /veiculos/estatisticas` | `String` JSON (`Content-Type` de texto) | `application/json` com as **mesmas chaves** `total`, `ativos`, `inativos`, `em_manutencao` (`@JsonProperty`) |
| `GET /veiculos/status/{status}` | só maiúsculas | case-insensitive |
| `PATCH /reativar` em veículo `MANUTENCAO` | `ATIVO` | 409 |

O front (`painel-admin`) consome essas rotas; verificar impacto antes de entregar (relacionado a `INC-FE1`/`INC-FE2` no mapa).

### 3.9 Notificação após commit (questão em aberto)

Com `@Transactional`, notificar dentro do método envia o e-mail antes do commit; se o commit falhar, o alerta fica órfão. Duas opções: (a) `@TransactionalEventListener(AFTER_COMMIT)` com evento de domínio — correto, um pouco mais código; (b) manter chamada direta e aceitar o risco, como hoje. **Recomendação: (a)**, porque o `VeiculoNotificador` já isola o ponto. Não resolve `X-6` (outbox/fila) — continua dívida.

---

## 4. Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Só mover o código para uma `VeiculoHelper` estática | Esconde o tamanho sem resolver acoplamento nem testabilidade. |
| Dividir por camada técnica (um `VeiculoValidationService`, um `VeiculoNotificationService`) e manter regra na service | Mantém a regra de negócio no mesmo lugar; escolhido o recorte por responsabilidade de domínio. |
| Bean Validation (`@Valid`) em vez de `VeiculoValidator` | Complementar, não substituto: cobre campos do DTO (`SEC-F4`), mas regras que dependem de estado (duplicidade, regressão de km, transição) continuam na camada de serviço. Pode ser adotado depois em cima do validador. |
| State pattern completo (uma classe por status) | Exagero para 3 estados e 4 transições; enum com tabela de transição basta. |
| Manter `RuntimeException` e só melhorar mensagens | Não resolve VS-08/VS-09. |
| Reescrever tudo de uma vez | Sem testes de caracterização atuais, risco alto; ver ordem na §5. |

---

## 5. Plano de execução sugerido

Cada passo termina com `mvn test` verde.

1. **Rede de segurança**: adicionar JaCoCo (só relatório) e escrever testes de *caracterização* da `VeiculoService` atual (comportamento de hoje, inclusive os bugs VS-11/13, marcados `@Disabled` com o comportamento desejado).
2. **Enums + exceções + advice**: `VeiculoStatus`, hierarquia `RotalogException`, `GlobalExceptionHandler`; ajustar `Veiculo`, repository, `ManutencaoService`, controller e testes. Sem divisão de classes ainda.
3. **Extrair** `VeiculoValidator`, `VeiculoNotificador`, `ManutencaoPreventivaPolicy`, `EstatisticasFrotaService` (cada um com seu teste).
4. **Extrair** `VeiculoStatusService` e `VeiculoQuilometragemService`; construtor + `@Transactional`; `@PrePersist/@PreUpdate`.
5. **Corrigir bugs** (§3.5), reabilitando os testes `@Disabled`.
6. **Limpeza**: Javadoc, `System.out`, variáveis e código morto.
7. **Gate**: ativar `jacoco:check` (≥ 90% linha no escopo) no `verify`.

Estimativa grosseira: 2 a 3 dias de uma pessoa, a maior parte em testes.

---

## 6. Questões em aberto (decisão do time)

1. **Regressão de quilometragem**: rejeitar sempre (proposto) ou permitir com perfil/flag de "correção manual"?
2. **`agendarManutencaoPreventiva`**: remover (não agenda nada, VS-26), renomear para `notificarManutencaoPreventiva`, ou implementar agendamento real via `ManutencaoService`?
3. **Métodos sem endpoint** (VS-29): confirmar que podem sair; se alguém fora do repo os usa, manter com teste.
4. **Cobertura 90%**: apenas linha, ou linha **e** branch? Escopo apenas dos pacotes de serviço (proposto) ou do projeto inteiro? Medir o projeto inteiro hoje daria um número bem mais baixo (controllers de `Motorista` e `Manutencao` e suas services não têm teste).
5. **Alerta de manutenção**: notificar uma vez ao cruzar o limite (proposto) ou a cada atualização acima dele (como hoje)?
6. **Destinatário** (`gestor@rotalog.com`): mover para `application.properties` (`rotalog.notificacao.destinatario-gestor`) — confirmar nome da chave e se vai por perfil.
7. **Enums de `Manutencao` e `Motorista`**: incluir neste trabalho ou ADR separado? (Proposta: separado; este ADR toca só `ManutencaoService` o mínimo necessário para compilar.)
8. **Notificação pós-commit**: opção (a) ou (b) da §3.9?
9. **Versão do Java**: o `pom` aponta Java 11. Se a migração `DEP-F1/F2` (Spring Boot 3 / Java 17+) estiver próxima, `record` para DTOs e `switch` com enum ficam mais simples — vale sequenciar.

---

## 7. Consequências

**Positivas**
- `VeiculoService` cai de 339 para algo em torno de 120-150 linhas; regras ficam localizáveis e testáveis.
- Erros distinguíveis por tipo; status HTTP correto sem `try/catch` repetido (remove ~10 blocos do controller).
- Status impossíveis de digitar errado; transições num único lugar.
- Cobertura passa de ≈0% para ≥ 90% no escopo, com gate no build.
- Fecha ou reduz no mapa: `ARQ-F2` (parcial), `ARQ-F4`, `ARQ-F5` (veículo), `ARQ-F6` (veículo), `INC-F2` (veículo), `INC-F3`, `INC-F4` (veículo), `QUA-F1` (veículo), `X-9` (veículo).

**Negativas / riscos**
- Mais classes e arquivos (≈ 7 de serviço, 7 exceções, 2 enums, 1 advice): mais navegação; mitigado por nomes por responsabilidade.
- Mudanças de contrato da §3.8 podem quebrar consumidores não mapeados; exige conferência com o front e com a api-entregas.
- Passar o status para enum toca `ManutencaoService`, `VeiculoRepository` e testes: PR maior do que "só a `VeiculoService`".
- Se algum registro já tem status fora dos 3 valores (a coluna não tem `CHECK`, ver `INC-F2`), a leitura com `@Enumerated` lança `IllegalArgumentException`. **Verificar o banco antes**: `SELECT DISTINCT status FROM frotas.veiculos;`.
- Testes de caracterização do passo 1 travam bugs conhecidos por um curto período; precisam ser removidos/invertidos no passo 5.

**Não resolve (continua no mapa)**: `ARQ-F1` (`TABLE_PER_CLASS`), `SEC-F1` (sem auth), `X-6` (notificação síncrona sem retry/circuit breaker), `INC-F6` (URL fixa do `NotificacaoClient`), `DEP-F1` (Spring Boot 2.7), `ARQ-F3` completo (DTO de resposta para todos os endpoints).

---

## 8. Referências

- [mapa-divida-tecnica.md](mapa-divida-tecnica.md) — seções 3 (frotas) e 7 (transversais)
- `rotalog-api-frotas/src/main/java/com/rotalog/service/VeiculoService.java`
- `rotalog-api-frotas/src/main/java/com/rotalog/service/ManutencaoService.java`
- `rotalog-api-frotas/src/main/java/com/rotalog/controller/VeiculoController.java`
- `rotalog-api-frotas/src/main/java/com/rotalog/domain/Veiculo.java`
- `rotalog-api-frotas/src/main/java/com/rotalog/repository/VeiculoRepository.java`
- `rotalog-api-frotas/src/test/java/com/rotalog/controller/VeiculoControllerTest.java`
- `.claude/agents/java-frotas.md`

*Limitações desta análise: baseada em leitura de código; nada foi compilado nem executado. Contagens de linha referem-se ao arquivo no commit atual da branch `feat/adding-route-again`. Não li `ManutencaoService` e `MotoristaService` por completo, apenas os pontos que tocam `Veiculo.status`.*
