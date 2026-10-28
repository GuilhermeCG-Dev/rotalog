# Análise do app rastreamento (React 18) e plano de modernização

Escopo: `rotalog-frontend/apps/rastreamento`. A análise é estática: não foram executados build nem testes. A versão mais atual do React não foi confirmada online; o conhecimento disponível é da linha 19.x. Rode `npm view react version` antes de definir o alvo.

## Problemas encontrados

### Segurança

- `MapView.tsx` (linhas 141-159) concatena `origem_endereco`, `destino_endereco`, `motorista_nome` e `descricao` em HTML de popup. O Leaflet usa `innerHTML` nos popups, o que permite XSS. É o achado mais grave.
- O Leaflet é carregado por script de CDN (`MapView.tsx:52`), embora `leaflet` e `react-leaflet` já estejam no `package.json`.

### Bugs

- `TrackingDashboard.tsx` (52-64): o polling de 5s faz fetch e descarta `data`. Só atualiza `lastUpdate`, então gasta rede sem atualizar a tela.
- `TrackingDashboard.tsx` (140): `.sort()` no `render` muta `delivery.rastreamentos`. O `interval` fica no state, quando deveria ser campo da instância. Não há `AbortController`. O badge usa `STATUS_CONFIG[event.evento]`, mas `evento` é `POSICAO_ATUALIZADA` e as chaves são status, então cai sempre em `status-default`.
- `MapView.tsx` (235): a polyline do trecho percorrido não é guardada em variável. Nunca é removida e vaza uma camada a cada seleção de entrega.
- A prop `center` só vale no `initMap`. O `mapCenter` do `App` muda, mas o mapa ignora.
- `DeliveryList.tsx` (16-49): `filteredDeliveries` é estado derivado de props (anti-pattern), o que exige `componentDidUpdate` e callback de `setState`. `onFilterChange` e `filters` do `App` não são usados.
- Duas URLs `http://localhost:3000` fixas. `res.ok` nunca é checado. Não há error boundary.
- `STATUS_CONFIG` duplicado em dois arquivos. As libs `shared-types` e `api-contracts` existem e não são usadas.
- O TODO de Redux não tem base: `redux` não está nas dependências. `reduxState: null` é código morto.
- `project.json` não tem target `test` e não há testes. O `lint` usa `@nx/linter`, que está deprecado.

## 1. Impacto de atualizar do React 18 para o mais atual

O código em si quase não quebra: `createRoot` já é usado, e não há string refs, legacy context nem `ReactDOM.render`. O custo está no entorno.

- **Nx 19.8.4:** o suporte a React 19 só existe em versões mais novas do Nx (20.x em diante). É preciso rodar `nx migrate` antes. É o maior custo.
- **`propTypes`:** o React 19 ignora `propTypes` sem avisar. Os 4 blocos viram código morto e a validação some em silêncio.
- **`react-leaflet` 4.2.1:** exige React 18. A v5 exige React 19. A ordem importa se a migração para react-leaflet for adotada.
- **`@types/react` 19:** `useRef` exige argumento, o namespace global `JSX` sai (usar `React.JSX`) e `children` não é mais implícito. Existem codemods oficiais.
- **Erros não capturados:** vão para `window.reportError`. Sem error boundary, adicionar um junto com o upgrade.
- **Dependências a checar:** `@pmmmwh/react-refresh-webpack-plugin` 0.5.x e `jest-environment-jsdom`.
- **Angular no mesmo `package.json`:** não há conflito com o Angular 18, mas as migrações do Nx tocam nos dois apps. Rodar `nx affected -t build` no painel-admin depois.
- **Sem testes:** não há rede de segurança. Escrever testes antes de qualquer upgrade.

## 2. Class components vs funcionais com hooks

Hoje são 4 classes, todas tipadas como `any, any`. O que se ganha:

- Elimina a classe de bug do `DeliveryList`: `useMemo` deriva a lista ordenada de `deliveries` e `sortBy`, sem estado duplicado.
- Cleanup junto do efeito. Um `usePolling(id)` com `AbortController` resolve o intervalo no state e o fetch descartado. Um `useLeafletMap` resolve init, remoção de camadas e destroy.
- Hooks próprios (`useDeliveries`, `useLeafletMap`) separam lógica de apresentação. Hoje `TrackingDashboard` mistura os dois.
- Testes mais simples com React Testing Library.
- Compatível com React Compiler, `use()`, Suspense e `ref` como prop, que só funcionam com function components.
- Menos boilerplate: sem constructor, sem `this`, sem arrow fields.

Riscos: closures obsoletas, dependências de `useEffect` e o StrictMode, que roda o efeito duas vezes em dev. O efeito do mapa precisa de cleanup correto.

## 3. PropTypes vs TypeScript: riscos

- **PropTypes** valida só em runtime e só em dev. Não pega erro em compilação. O React 19 não valida mais nada. `PropTypes.object` não descreve o formato.
- **TS hoje não protege:** `strict: false`, `noImplicitAny: false` e `any` em tudo (`tsconfig.app.json`). Trocar PropTypes por `any` seria só trocar de nome.
- **Migração:** ligar `strict` de uma vez gera uma enxurrada de erros. Fazer por etapas, com tipos reais de `shared-types`.
- **TS não valida o payload da API.** Os campos `snake_case` chegam do back sem checagem em runtime. Para isso, usar zod ou parse manual na borda do fetch.
- **Deriva de contrato:** tipos redefinidos no app divergem de `api-contracts`. Importar das libs, sem duplicar.
- **Remover PropTypes sem substituto** deixa o app sem nenhuma validação. Fazer TS e remoção no mesmo PR.

## 4. Ordem por esforço vs benefício

Esforço: P = pequeno, M = médio, G = grande.

0. **Pré-requisito: testes de fumaça (P-M).** Adicionar target `test` e testes com RTL para os 3 componentes.
1. **Corrigir XSS dos popups (P, benefício muito alto).** Usar `textContent`/nós DOM ou escapar o HTML.
2. **Bugs pequenos (P, alto).** Polyline vazando, `sort` mutando props, polling inútil, `res.ok`, badge errado.
3. **Centralizar `API_BASE_URL` e `STATUS_CONFIG` (P, médio).** Variável de ambiente e `shared-types`.
4. **Tipos reais no lugar de `any` (M, alto).** Importar de `shared-types` e remover PropTypes. Depois ligar `strict` por etapas (M-G, alto a longo prazo).
5. **Classes para funções + hooks (M, alto).** Antes do upgrade, para reduzir a superfície de mudança.
6. **Leaflet via npm (M, médio-alto).** Começar com `import L from 'leaflet'` dentro de um `useLeafletMap`. Remove o CDN e o `declare const L`. O comentário de que o webpack do Nx quebra com react-leaflet não tem evidência; testar antes de aceitar.
7. **TanStack Query no lugar do "Redux" (M, alto).** O estado é pequeno e quase todo dado de servidor. `refetchInterval` substitui o polling manual. Redux Toolkit seria exagero.
8. **Atualizar para React 19 (M-G por causa do `nx migrate`, médio).** Por último. O benefício é manter ecossistema e suporte. Os ganhos de código vêm dos itens 4 e 5.

Os itens 1 a 3 cabem em um PR pequeno.
