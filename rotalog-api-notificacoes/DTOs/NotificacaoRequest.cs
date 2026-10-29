namespace api_notificacoes.DTOs;

/// <summary>
/// DTO para criação de notificação
/// 
/// FIXME: Sem FluentValidation
/// FIXME: Sem validação de canal
/// FIXME: Sem validação de tipo
/// </summary>
public class NotificacaoRequest
{
    public string Tipo { get; set; } = string.Empty;
    public string? Canal { get; set; } = "email";
    public string Destinatario { get; set; } = string.Empty;
    public string Mensagem { get; set; } = string.Empty;
    public string? Assunto { get; set; }
    public string? ServicoOrigem { get; set; }
    public string? ReferenciaId { get; set; }
    
    // Variáveis para substituição no template
    public Dictionary<string, string>? Variaveis { get; set; }
}

/// <summary>
/// DTO para resposta de notificação
/// </summary>
public class NotificacaoResponse
{
    public long Id { get; set; }
    public string Tipo { get; set; } = string.Empty;
    public string Canal { get; set; } = string.Empty;
    public string Destinatario { get; set; } = string.Empty;
    public string? Assunto { get; set; }
    public string Mensagem { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public int Tentativas { get; set; }
    public string? ErroMensagem { get; set; }
    public string? ServicoOrigem { get; set; }
    public string? ReferenciaId { get; set; }
    public DateTime DataCriacao { get; set; }
    public DateTime? DataEnvio { get; set; }
}

/// <summary>
/// DTO do alerta de manutenção preventiva enviado pela api-frotas
/// (contrato: AlertaManutencaoNotificacaoRequest em rotalog-api-frotas)
/// </summary>
public class AlertaManutencaoRequest
{
    public long VeiculoId { get; set; }
    public string Placa { get; set; } = string.Empty;
    public string? Modelo { get; set; }
    public string Motivo { get; set; } = string.Empty; // QUILOMETRAGEM, TEMPO, QUILOMETRAGEM_E_TEMPO
    public long? Quilometragem { get; set; }
    public string? DataUltimaManutencao { get; set; } // ISO-8601, null quando nunca houve manutenção
    public string Destinatario { get; set; } = string.Empty;
    public string? ServicoOrigem { get; set; }
    public long? ReferenciaId { get; set; } // id do alerta na api-frotas
}

/// <summary>
/// DTO para reenvio de notificação
/// </summary>
public class ReenvioRequest
{
    public string? NovoDestinatario { get; set; }
    public string? NovoCanal { get; set; }
}

/// <summary>
/// DTO para estatísticas
/// </summary>
public class NotificacaoStats
{
    public int Total { get; set; }
    public int Enviadas { get; set; }
    public int Pendentes { get; set; }
    public int Falhas { get; set; }
    public Dictionary<string, int> PorTipo { get; set; } = new();
    public Dictionary<string, int> PorCanal { get; set; } = new();
    public DateTime GeradoEm { get; set; } = DateTime.UtcNow;
}
