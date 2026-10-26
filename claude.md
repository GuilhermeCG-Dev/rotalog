# Rotalog

## Visão Geral

Sistema de gestão de frotas e entregas. Gerência de veículos, motoristas, entrega e notificação. Composto por 3 APIs de back-end e 2 front-ends (Monorepo nx).

## Repositórios

### rotalog-api-frotas (Java/Spring boot 2.7)
- Responsável por pedidos, roteamento e tracking
- Banco postegresSQL
- Migrations: Flyway 
- Estrutura: controller -> service -> repository
- Entidades: JPA em models

### rotalog-api-entregas (Node.js/Express 4)
- Responsável por entregas e rastreamento; consome api-frotas e api-notificacoes
- Banco postgreSQL via Sequelize (pg)
- Migrations: SQL manual em src/config (migration.sql, seed.sql)
- Estrutura: routes -> services -> models
- Entidades: Sequelize em models (Entrega, Rastreamento)
- Auth: middleware em src/middleware/auth.js
- Porta 3000

### rotalog-api-notificacoes (.NET 6/ASP.NET Core)
- Responsável por notificações, templates e configurações de notificação
- Banco postgreSQL via Entity Framework Core (Npgsql)
- Migrations: SQL manual em Data (migration.sql, seed.sql); startup usa EnsureCreated
- Estrutura: Controllers -> Services (NotificacaoService) -> DbContext
- Entidades: EF em Models; DTOs em DTOs
- Swagger em /swagger
- Porta 5000

### rotalog-frontend (Nx, Angular 18 + React 18)
- apps/painel-admin: painel administrativo (Angular)
- apps/rastreamento: rastreamento de entregas (React, Leaflet)
- libs: api-contracts, shared-types, ui-components
- Testes: Jest (unit), Playwright (e2e em painel-admin-e2e)
- Rodar tarefas via nx (npx nx run-many ...)
- Ver CLAUDE.md/AGENTS.md do repositório

### rotalog-workspace (Docker)
- Infra local: docker-compose com postgreSQL 14 (container rotalog-db, porta 5432)
- Scripts SQL em tools/scripts: init-schemas, migrations e seeds das 3 APIs, executados em ordem numérica
- Resetar banco: docker-compose down -v

