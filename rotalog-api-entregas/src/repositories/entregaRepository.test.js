jest.mock('../models', () => ({
    Entrega: {
        findAll: jest.fn(),
        findByPk: jest.fn(),
        findOne: jest.fn(),
        create: jest.fn()
    },
    Rastreamento: { create: jest.fn() }
}));

const { Entrega, Rastreamento } = require('../models');
const repo = require('./entregaRepository');

describe('entregaRepository', () => {
    beforeEach(() => jest.clearAllMocks());

    it('listar filtra, ordena e inclui os 5 últimos rastreamentos', async () => {
        Entrega.findAll.mockResolvedValue([{ id: 1 }]);

        const resultado = await repo.listar({ status: 'PENDENTE' });

        expect(resultado).toEqual([{ id: 1 }]);
        const opcoes = Entrega.findAll.mock.calls[0][0];
        expect(opcoes.where).toEqual({ status: 'PENDENTE' });
        expect(opcoes.order).toEqual([['data_criacao', 'DESC']]);
        expect(opcoes.include[0]).toMatchObject({ model: Rastreamento, as: 'rastreamentos', limit: 5 });
    });

    it('buscarComRastreamentos inclui rastreamentos', async () => {
        await repo.buscarComRastreamentos(1);

        expect(Entrega.findByPk).toHaveBeenCalledWith(1, {
            include: [{ model: Rastreamento, as: 'rastreamentos', order: [['data_evento', 'DESC']] }]
        });
    });

    it('buscarPorId repassa opções', async () => {
        await repo.buscarPorId(1, { transaction: 'tx' });
        await repo.buscarPorId(2);

        expect(Entrega.findByPk).toHaveBeenNthCalledWith(1, 1, { transaction: 'tx' });
        expect(Entrega.findByPk).toHaveBeenNthCalledWith(2, 2, {});
    });

    it('buscarPorPedido filtra por numero_pedido', async () => {
        await repo.buscarPorPedido('PED-1');

        expect(Entrega.findOne).toHaveBeenCalledWith({ where: { numero_pedido: 'PED-1' } });
    });

    it('criar repassa dados e opções', async () => {
        await repo.criar({ a: 1 }, { transaction: 'tx' });
        await repo.criar({ b: 2 });

        expect(Entrega.create).toHaveBeenNthCalledWith(1, { a: 1 }, { transaction: 'tx' });
        expect(Entrega.create).toHaveBeenNthCalledWith(2, { b: 2 }, {});
    });

    it('salvar chama save com as opções', async () => {
        const entrega = { save: jest.fn().mockResolvedValue('ok') };

        await repo.salvar(entrega, { transaction: 'tx' });
        await repo.salvar(entrega);

        expect(entrega.save).toHaveBeenNthCalledWith(1, { transaction: 'tx' });
        expect(entrega.save).toHaveBeenNthCalledWith(2, {});
    });

    it('registrarEvento cria rastreamento com data', async () => {
        await repo.registrarEvento(1, 'EVENTO', 'desc', { transaction: 'tx' });
        await repo.registrarEvento(2, 'EVENTO', 'desc');

        expect(Rastreamento.create).toHaveBeenNthCalledWith(
            1,
            { entrega_id: 1, evento: 'EVENTO', descricao: 'desc', data_evento: expect.any(Date) },
            { transaction: 'tx' }
        );
        expect(Rastreamento.create.mock.calls[1][1]).toEqual({});
    });

    it('contarPorStatus converte agregados para número', async () => {
        Entrega.findAll.mockResolvedValue([
            { status: 'PENDENTE', total: '2', distancia_media: '10.50', peso_medio: '0' }
        ]);

        const resultado = await repo.contarPorStatus();

        expect(resultado).toEqual([
            { status: 'PENDENTE', total: 2, distancia_media: 10.5, peso_medio: 0 }
        ]);
        expect(Entrega.findAll.mock.calls[0][0]).toMatchObject({ group: ['status'], raw: true });
    });
});
