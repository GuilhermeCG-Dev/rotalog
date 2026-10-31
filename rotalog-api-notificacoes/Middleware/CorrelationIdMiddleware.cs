namespace api_notificacoes.Middleware;

/// <summary>
/// Lê o header X-Correlation-ID (gerado pela api-frotas), devolve-o na resposta
/// e o inclui como escopo de log em toda a requisição.
/// Se o header não vier, gera um novo valor.
/// </summary>
public class CorrelationIdMiddleware
{
    public const string HeaderName = "X-Correlation-ID";

    private readonly RequestDelegate _next;
    private readonly ILogger<CorrelationIdMiddleware> _logger;

    public CorrelationIdMiddleware(RequestDelegate next, ILogger<CorrelationIdMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var correlationId = context.Request.Headers[HeaderName].FirstOrDefault();
        if (string.IsNullOrWhiteSpace(correlationId))
            correlationId = Guid.NewGuid().ToString();

        context.Response.Headers[HeaderName] = correlationId;

        using (_logger.BeginScope(new Dictionary<string, object> { ["correlationId"] = correlationId }))
        {
            await _next(context);
        }
    }
}
