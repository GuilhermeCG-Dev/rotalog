const express = require('express');
const asyncHandler = require('../middleware/asyncHandler');
const entregaServiceDefault = require('../services/entregaServiceDefault');

const criarEntregasRouter = (entregaService) => {
    const router = express.Router();

    router.get('/', asyncHandler(async (req, res) => {
        res.json(await entregaService.listar(req.query));
    }));

    router.get('/stats', asyncHandler(async (req, res) => {
        res.json(await entregaService.estatisticas());
    }));

    router.get('/pedido/:numeroPedido', asyncHandler(async (req, res) => {
        res.json(await entregaService.buscarPorPedido(req.params.numeroPedido));
    }));

    router.get('/:id', asyncHandler(async (req, res) => {
        res.json(await entregaService.buscarPorId(req.params.id));
    }));

    router.post('/', asyncHandler(async (req, res) => {
        res.status(201).json(await entregaService.criar(req.body));
    }));

    router.put('/:id', asyncHandler(async (req, res) => {
        res.json(await entregaService.atualizar(req.params.id, req.body));
    }));

    router.patch('/:id/status', asyncHandler(async (req, res) => {
        res.json(await entregaService.alterarStatus(req.params.id, req.body.status));
    }));

    router.patch('/:id/atribuir', asyncHandler(async (req, res) => {
        res.json(await entregaService.atribuir(req.params.id, req.body));
    }));

    router.delete('/:id', asyncHandler(async (req, res) => {
        const entrega = await entregaService.cancelar(req.params.id);
        res.json({ message: 'Entrega cancelada', entrega });
    }));

    return router;
};

module.exports = criarEntregasRouter(entregaServiceDefault);
module.exports.criarEntregasRouter = criarEntregasRouter;
