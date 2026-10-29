-- RotaLog - Migration: alertas de manutenção preventiva (api-frotas)
-- Este script é idempotente (pode ser executado múltiplas vezes)
-- Executar após os scripts 01-08

SET search_path TO frotas;

-- Tabela de alertas de manutenção preventiva
-- status: PENDENTE (aguardando envio / api-notificacoes fora), ENVIADO, FALHA
-- motivo: QUILOMETRAGEM, TEMPO, QUILOMETRAGEM_E_TEMPO
CREATE TABLE IF NOT EXISTS alertas_manutencao (
    id BIGSERIAL PRIMARY KEY,
    veiculo_id BIGINT NOT NULL,
    placa VARCHAR(7),
    motivo VARCHAR(30) NOT NULL,
    quilometragem BIGINT,
    data_ultima_manutencao TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    notificacao_id BIGINT,
    erro_mensagem TEXT,
    data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_status ON alertas_manutencao (status);
CREATE INDEX IF NOT EXISTS idx_alertas_manutencao_veiculo_id ON alertas_manutencao (veiculo_id);

-- FIXME: Sem FK para veiculos (mesmo padrão de manutencoes)
