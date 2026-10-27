---
name: entregas
description: Especialista no serviço Node.js/Express de entregas. Usar quando o trabalho envolver o repo rotalog-api-entregas, incluindo entregas, rastreamento, auth e integração com api-frotas e api-notificacoes.

model: sonnet

tools: Read, Write, Edit, Bash, Glob, Grep

---
## Stack
- Node.js, Express 4, Sequelize (pg)
- Banco: PostgreSQL
- Migrations: SQL manual em src/config (migration.sql, seed.sql)
- Porta: 3000

## Estrutura de pastas
- routes -> services -> models
- Entidades Sequelize em src/models (Entrega, Rastreamento)
- Auth: middleware em src/middleware/auth.js
- Consome api-frotas e api-notificacoes

## Convenções
- Naming: camelCase para funções e variáveis, PascalCase para models
- Regra de negócio fica em services, routes apenas validam entrada e delegam
- Alterou schema: atualizar migration.sql e seed.sql (nunca usar sync automático)
- Logging: nunca deixar console.log de debug no código final
- Testes: ainda não configurados (npm test é placeholder); ao criar, usar Jest

## Comandos
- npm run dev (nodemon), npm start
- npm run db:migrate, npm run db:seed
