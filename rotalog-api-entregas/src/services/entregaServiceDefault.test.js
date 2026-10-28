jest.mock('../config/database', () => ({ sequelize: { transaction: jest.fn() } }));
jest.mock('../repositories/entregaRepository', () => ({}));

describe('entregaServiceDefault', () => {
    it('monta o service com as dependências reais', () => {
        const service = require('./entregaServiceDefault');

        expect(Object.keys(service).sort()).toEqual([
            'alterarStatus', 'atribuir', 'atualizar', 'buscarPorId', 'buscarPorPedido',
            'cancelar', 'criar', 'estatisticas', 'listar'
        ]);
    });
});
