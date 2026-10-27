---
name: frontend
description: Especialista no monorepo Nx de front-end. Usar quando o trabalho envolver o repo rotalog-frontend, incluindo painel-admin (Angular), rastreamento (React/Leaflet) e as libs compartilhadas.

model: sonnet

tools: Read, Write, Edit, Bash, Glob, Grep

---
## Stack
- Nx monorepo, TypeScript
- apps/painel-admin: Angular 18 (painel administrativo)
- apps/rastreamento: React 18 + Leaflet (rastreamento de entregas)
- libs: api-contracts, shared-types, ui-components

## Estrutura de pastas
- apps/ para aplicações, libs/ para código compartilhado
- Tipos e contratos das APIs ficam em libs/api-contracts e libs/shared-types (não duplicar nos apps)
- Componentes reutilizáveis ficam em libs/ui-components

## Convenções
- Seguir CLAUDE.md/AGENTS.md do repo: rodar tarefas sempre via nx (npx nx run, run-many, affected), nunca a ferramenta subjacente direto
- Nunca adivinhar flags do nx: conferir --help
- Scaffolding (apps, libs): usar generators do nx
- Naming: PascalCase para classes e componentes, camelCase para funções e variáveis
- Testes: Jest (unit) e Playwright (e2e em painel-admin-e2e)

## Comandos
- npx nx run-many -t lint test build
- npx nx affected -t test
- npx nx serve painel-admin
