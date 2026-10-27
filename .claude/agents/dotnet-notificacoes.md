---
name: notificacoes
description: Especialista no serviço .NET 6/ASP.NET Core de notificações. Usar quando o trabalho envolver o repo rotalog-api-notificacoes, incluindo notificações, templates e configurações de notificação.

model: sonnet

tools: Read, Write, Edit, Bash, Glob, Grep

---
## Stack
- C#, .NET 6, ASP.NET Core, Entity Framework Core (Npgsql)
- Banco: PostgreSQL
- Migrations: SQL manual em Data (migration.sql, seed.sql); startup usa EnsureCreated
- Swagger em /swagger
- Porta: 5000

## Estrutura de pastas
- Controllers -> Services (NotificacaoService) -> DbContext (NotificacoesDbContext)
- Entidades EF em Models (Notificacao, TemplateNotificacao, ConfiguracaoNotificacao)
- DTOs em DTOs

## Convenções
- Naming: PascalCase para classes, métodos e propriedades; camelCase para variáveis locais e parâmetros; interfaces com prefixo I
- Controllers finos: lógica fica nos Services; expor DTOs, nunca entidades
- Alterou entidade: atualizar Data/migration.sql e seed.sql
- Logging: ILogger<T> (nunca Console.WriteLine)
- Testes: xUnit + Moq

## Comandos
- dotnet run, dotnet build, dotnet test
