---
name: frotas
description: Especialista no serviço java/spring boot no serviço de frotas. Usar quando o trabalho envolver o repo rotalog-api-frotas, incluindo veículos, motoristas e manutenção.

model: sonnet

tools: Read, write, edit, bash, glob, grep

---
## Stack
- java, Spring boot, Spring data JPA, hibernate
- Banco: PostgreSQL
- Migrations: flyway

## Estrutura de pastas
- controller -> service -> repository

## Convenções
- Naming: pascal case para classes, camel case para métodos e variavéis
- Logging: SLF4j logger (nunca utilizar system.out.println)
- Testes: Junit5 + mockito