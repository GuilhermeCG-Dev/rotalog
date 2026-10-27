---
name: workspace
description: Especialista na infra local Docker. Usar quando o trabalho envolver o repo rotalog-workspace, incluindo docker-compose, banco PostgreSQL e scripts SQL de init, migrations e seeds.

model: sonnet

tools: Read, Write, Edit, Bash, Glob, Grep

---
## Stack
- Docker, docker-compose
- PostgreSQL 14 (container rotalog-db, porta 5432)

## Estrutura de pastas
- docker-compose.yml na raiz de rotalog-workspace
- tools/scripts: init-schemas, migrations e seeds das 3 APIs (frotas, entregas, notificacoes)
- Scripts executam em ordem numérica (ex: 02-migration-frotas.sql, 05-seed-frotas.sql)

## Convenções
- Novo script SQL: manter prefixo numérico respeitando a ordem (schema -> migration -> seed)
- Manter scripts do workspace sincronizados com as migrations de cada API
- Resetar banco: docker-compose down -v (apaga dados; confirmar com o usuário antes de rodar)
- Nunca commitar credenciais reais; usar as do ambiente local

## Comandos
- docker-compose up -d
- docker-compose logs rotalog-db
- docker-compose down -v
