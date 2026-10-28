const { fn, col } = require('sequelize');
const { Entrega, Rastreamento } = require('../models');

const LIMITE_RASTREAMENTOS_LISTAGEM = 5;

const listar = (where) => Entrega.findAll({
    where,
    order: [['data_criacao', 'DESC']],
    include: [{
        model: Rastreamento,
        as: 'rastreamentos',
        limit: LIMITE_RASTREAMENTOS_LISTAGEM,
        order: [['data_evento', 'DESC']]
    }]
});

const buscarComRastreamentos = (id) => Entrega.findByPk(id, {
    include: [{
        model: Rastreamento,
        as: 'rastreamentos',
        order: [['data_evento', 'DESC']]
    }]
});

const buscarPorId = (id, opcoes = {}) => Entrega.findByPk(id, opcoes);

const buscarPorPedido = (numeroPedido) => Entrega.findOne({ where: { numero_pedido: numeroPedido } });

const criar = (dados, opcoes = {}) => Entrega.create(dados, opcoes);

const salvar = (entrega, opcoes = {}) => entrega.save(opcoes);

const registrarEvento = (entregaId, evento, descricao, opcoes = {}) => Rastreamento.create({
    entrega_id: entregaId,
    evento,
    descricao,
    data_evento: new Date()
}, opcoes);

const contarPorStatus = async () => {
    const linhas = await Entrega.findAll({
        attributes: [
            'status',
            [fn('COUNT', col('id')), 'total'],
            [fn('COALESCE', fn('AVG', col('distancia_km')), 0), 'distancia_media'],
            [fn('COALESCE', fn('AVG', col('peso_kg')), 0), 'peso_medio']
        ],
        group: ['status'],
        raw: true
    });

    return linhas.map((linha) => ({
        status: linha.status,
        total: Number(linha.total),
        distancia_media: Number(linha.distancia_media),
        peso_medio: Number(linha.peso_medio)
    }));
};

module.exports = {
    listar,
    buscarComRastreamentos,
    buscarPorId,
    buscarPorPedido,
    criar,
    salvar,
    registrarEvento,
    contarPorStatus
};
