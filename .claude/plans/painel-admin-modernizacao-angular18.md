# Painel-admin: auditoria Angular 18

Escopo: `rotalog-frontend/apps/painel-admin`. Todos os arquivos TS foram lidos (dashboard, entregas, layout, manutencoes, motoristas, veiculos, services, rotas, config). Não foi rodado build nem medido o bundle.

## HttpClient

Nenhuma chamada usa HttpClient.

- `services/frotas.service.ts` e `services/entregas.service.ts` usam `fetch()` em 100% das chamadas, com `async/await`.
- `app.config.ts` não tem `provideHttpClient()`.
- Não há interceptors. Auth, erro global e base URL não podem ser centralizados.
- Todo erro vira `return []` ou `null`. A UI não distingue "sem dados" de "API caiu".
- As URLs `localhost:8080` e `localhost:3000` estão hardcoded.
- O cache manual nunca invalida `manutencoes`.

## Lazy loading

Viável e trivial. Todos os componentes já são `standalone: true`, então não precisa de módulo.

- `app.routes.ts` importa os 5 componentes de forma eager. Todos entram no bundle principal.
- Trocar `component:` por `loadComponent: () => import(...).then(m => m.X)`.
- `AppComponent` e `SidebarComponent` ficam eager, pois são o shell.
- Opcional: `withPreloading(PreloadAllModules)` para pré-carregar em background.
- Maior ganho esperado: `VeiculosComponent` (464 linhas) e `DashboardComponent` (349 linhas).
- O ganho real depende do tamanho do bundle. Confirmar com `npx nx build painel-admin`.

## Modernizações, ordenadas por benefício ÷ esforço

| # | Mudança | Esforço | Benefício |
|---|---|---|---|
| 1 | Lazy loading com `loadComponent` nas 5 rotas | Baixo (~10 linhas) | Alto: bundle inicial menor |
| 2 | `navigateTo()` do dashboard usa `window.location.href` (reload total, perde estado da SPA). Trocar por `routerLink` ou `inject(Router)` | Baixo | Alto: corrige bug real |
| 3 | `@if` / `@for` no lugar de `*ngIf` / `*ngFor`, com `track` | Baixo (migração automática: `ng generate @angular/core:control-flow`) | Médio: menos imports, melhor performance. Remove `CommonModule` |
| 4 | `provideHttpClient(withFetch(), withInterceptors([...]))` e migrar os 2 services para `HttpClient` | Médio | Alto: tipagem, interceptors, erros, testabilidade |
| 5 | `environment` / `InjectionToken` para as URLs | Baixo | Médio |
| 6 | `inject()` no lugar de injeção por constructor | Baixo (schematic `inject`) | Baixo a médio |
| 7 | Dashboard: `Promise.all` ou `forkJoin` no lugar de 3 `await` sequenciais | Baixo | Médio: carga ~3x mais rápida |
| 8 | Signals: `signal` / `computed` para `loading`, listas e filtros | Médio | Alto |
| 9 | `ChangeDetectionStrategy.OnPush` em todos os componentes | Baixo, junto com signals | Médio |
| 10 | Reactive Forms (`FormBuilder` tipado) no lugar de `[(ngModel)]` e `alert()` | Médio | Médio: validação real. Hoje só checa campo vazio |
| 11 | Remover duplicação entre Veículos e Motoristas (lista, filtro e form) com componentes de apresentação e `input()` / `output()` | Alto | Alto a longo prazo |
| 12 | Guards funcionais, resolvers e página 404 (TODOs existentes) | Médio | Médio |
| 13 | `provideRouter(..., withComponentInputBinding(), withViewTransitions())` | Baixo | Baixo |
| 14 | Mover `template` e `styles` inline para arquivos `.html` / `.css` | Médio | Baixo |

## Notas por componente

- **dashboard**
  - 10 campos soltos viram `computed()`.
  - `alertas` é fixo e `totalManutencoes = 6` / `manutencoesPendentes = 2` são hardcoded, então os números exibidos são falsos.
  - Dividir em cards e uma tabela de últimas entregas.
- **entregas**
  - `countByStatus()` roda o filter a cada ciclo de detecção. Vira `computed()`.
  - `entregasFiltradas` vira `computed()` a partir de `termoBusca` e `filtroStatus` (signals). Com isso `(input)="filtrar()"` some.
- **layout (sidebar)**
  - Já é standalone e simples.
  - Remover `CommonModule` e adicionar `OnPush`.
  - A lista de links vira array com `@for`.
- **manutencoes**
  - `alert` exposto no template é anti-pattern. Remover ou implementar o botão.
  - Tabela simples, bom candidato inicial para provar o padrão novo (signals, `@for`, `HttpClient`).
- **motoristas / veiculos**
  - Mesmo padrão copiado.
  - `ordenar()` usa `any`.
  - `.sort()` muta o array no lugar.
  - Chama `limparCache()` depois de `criar` / `atualizar`, que já invalidam o cache. Redundante.
  - Ambos precisam de Reactive Forms e extração de form e lista.

## Ordem sugerida de execução

1. Passos 1, 2, 3, 5, 6 e 13: baixo risco, quase todos automáticos ou mecânicos.
2. Passo 4: `HttpClient`, com interceptor de erro e base URL. Depois 7.
3. Passos 8, 9 e 10: signals, `OnPush` e Reactive Forms, componente por componente. Começar por manutencoes.
4. Passos 11 e 12: refatoração estrutural.

## Observações

- Em Angular 18, `input()` / `output()` e `toSignal` ainda são developer preview. Funcionam, mas a API pode mudar.
- Zoneless (`provideExperimentalZonelessChangeDetection`) ainda é experimental no 18. Não adotar agora.

## Próximo passo proposto

Aplicar primeiro lote de baixo esforço: passos 1, 2, 3 e 5.
