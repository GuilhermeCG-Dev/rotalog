-- RotaLog - Seed: veículos elegíveis para alerta de manutenção preventiva
-- Este script é idempotente (pode ser executado múltiplas vezes)
-- Executar após os scripts 01-09
--
-- Regra de elegibilidade (api-frotas): veículo ATIVO com
--   quilometragem >= rotalog.manutencao.limite-quilometragem (padrão 50000) OU
--   última manutenção PREVENTIVA/REVISAO CONCLUIDA há mais de rotalog.manutencao.meses-limite meses (padrão 6)
--   (sem manutenção concluída, vale a data de cadastro)
-- As datas são relativas a CURRENT_TIMESTAMP para o seed não "envelhecer".

SET search_path TO frotas;

-- Veículos
INSERT INTO veiculos (placa, modelo, ano_fabricacao, quilometragem, status, data_cadastro, data_atualizacao) VALUES
-- Elegível só por quilometragem (manutenção recente)
('KMA1B23', 'Mercedes Accelo 815', 2020, 85000, 'ATIVO', CURRENT_TIMESTAMP - INTERVAL '2 years', CURRENT_TIMESTAMP),
-- Elegível só por tempo (8 meses sem manutenção, km baixa)
('TMP2C34', 'Renault Kangoo', 2022, 20000, 'ATIVO', CURRENT_TIMESTAMP - INTERVAL '2 years', CURRENT_TIMESTAMP),
-- Elegível por quilometragem e tempo
('AMB3D45', 'VW Constellation 24.280', 2018, 130000, 'ATIVO', CURRENT_TIMESTAMP - INTERVAL '3 years', CURRENT_TIMESTAMP),
-- Elegível por tempo: nunca fez manutenção, cadastrado há 10 meses
('SEM4E56', 'Fiat Strada', 2023, 10000, 'ATIVO', CURRENT_TIMESTAMP - INTERVAL '10 months', CURRENT_TIMESTAMP),
-- Controle: NÃO elegível (km baixa, manutenção há 1 mês)
('OKK5F67', 'Iveco Daily 70C17', 2023, 12000, 'ATIVO', CURRENT_TIMESTAMP - INTERVAL '1 year', CURRENT_TIMESTAMP),
-- Controle: NÃO elegível (INATIVO, mesmo com km alta)
('INA6G78', 'Ford Cargo 816', 2015, 200000, 'INATIVO', CURRENT_TIMESTAMP - INTERVAL '4 years', CURRENT_TIMESTAMP)
ON CONFLICT (placa) DO NOTHING;

-- Manutenções concluídas (SEM4E56 e INA6G78 ficam sem histórico de propósito)
INSERT INTO manutencoes (veiculo_id, tipo_manutencao, data_manutencao, quilometragem_manutencao, custo, descricao, status, data_criacao, data_atualizacao)
SELECT v.id, m.tipo, CURRENT_TIMESTAMP - m.atras, m.km, m.custo, m.descricao, 'CONCLUIDA', CURRENT_TIMESTAMP - m.atras, CURRENT_TIMESTAMP - m.atras
FROM (VALUES
    ('KMA1B23', 'PREVENTIVA', INTERVAL '20 days',  84000, 1200.00, 'Troca de óleo e filtros'),
    ('TMP2C34', 'PREVENTIVA', INTERVAL '8 months', 15000,  700.00, 'Troca de óleo e filtros'),
    ('AMB3D45', 'REVISAO',    INTERVAL '14 months', 110000, 2300.00, 'Revisão geral - 110.000 km'),
    ('OKK5F67', 'PREVENTIVA', INTERVAL '1 month',  11000,  650.00, 'Troca de óleo e filtros')
) AS m(placa, tipo, atras, km, custo, descricao)
JOIN veiculos v ON v.placa = m.placa
WHERE NOT EXISTS (
    SELECT 1 FROM manutencoes x
    WHERE x.veiculo_id = v.id AND x.descricao = m.descricao AND x.quilometragem_manutencao = m.km
);
