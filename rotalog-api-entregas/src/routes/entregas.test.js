const request = require('supertest');
const express = require('express');

jest.mock('../services/entregaServiceDefault', () => ({}));

const { criarEntregasRouter } = require('./entregas');
const { NotFoundError, ConflictError, ValidationError } = require('../errors');

const metodos = [
    'listar', 'buscarPorId', 'buscarPorPedido', 'estatisticas',
    'criar', 'atualizar', 'alterarStatus', 'atribuir', 'cancelar'
];

const criarApp = (service) => {
    const app = express();
    app.use(express.json());
    app.use('/api/entregas', criarEntregasRouter(service));
    // eslint-disable-next-line no-unused-vars
    app.use((err, req, res, next) => res.status(500).json({ error: 'interno' }));
    return app;
};

describe('rotas de entregas', () => {
    let service;
    let app;

    beforeEach(() => {
        service = Object.fromEntries(metodos.map((m) => [m, jest.fn()]));
        app = criarApp(service);
    });

    it('GET / delega a query para listar', async () => {
        service.listar.mockResolvedValue([{ id: 1 }]);

        const res = await request(app).get('/api/entregas').query({ status: 'pendente' });

        expect(res.status).toBe(200);
        expect(res.body).toEqual([{ id: 1 }]);
        expect(service.listar).toHaveBeenCalledWith({ status: 'pendente' });
    });

    it('GET /stats não é capturada por /:id', async () => {
        service.estatisticas.mockResolvedValue({ total: 0 });

        const res = await request(app).get('/api/entregas/stats');

        expect(res.body).toEqual({ total: 0 });
        expect(service.buscarPorId).not.toHaveBeenCalled();
    });

    it('GET /pedido/:numeroPedido delega ao service', async () => {
        service.buscarPorPedido.mockResolvedValue({ id: 1 });

        const res = await request(app).get('/api/entregas/pedido/PED-1');

        expect(res.status).toBe(200);
        expect(service.buscarPorPedido).toHaveBeenCalledWith('PED-1');
    });

    it('GET /:id devolve 404 quando service lança NotFoundError', async () => {
        service.buscarPorId.mockRejectedValue(new NotFoundError('Entrega não encontrada'));

        const res = await request(app).get('/api/entregas/9');

        expect(res.status).toBe(404);
        expect(res.body).toEqual({ error: 'Entrega não encontrada' });
    });

    it('GET /:id devolve a entrega', async () => {
        service.buscarPorId.mockResolvedValue({ id: 1 });

        const res = await request(app).get('/api/entregas/1');

        expect(res.body).toEqual({ id: 1 });
        expect(service.buscarPorId).toHaveBeenCalledWith('1');
    });

    it('POST / devolve 201', async () => {
        service.criar.mockResolvedValue({ id: 5 });

        const res = await request(app).post('/api/entregas').send({ origem_endereco: 'A' });

        expect(res.status).toBe(201);
        expect(service.criar).toHaveBeenCalledWith({ origem_endereco: 'A' });
    });

    it('POST / devolve 400 quando service lança ValidationError', async () => {
        service.criar.mockRejectedValue(new ValidationError('inválido'));

        const res = await request(app).post('/api/entregas').send({});

        expect(res.status).toBe(400);
        expect(res.body).toEqual({ error: 'inválido' });
    });

    it('PUT /:id delega id e body', async () => {
        service.atualizar.mockResolvedValue({ id: 1 });

        const res = await request(app).put('/api/entregas/1').send({ peso_kg: 2 });

        expect(res.status).toBe(200);
        expect(service.atualizar).toHaveBeenCalledWith('1', { peso_kg: 2 });
    });

    it('PATCH /:id/status delega status e devolve 409 em transição inválida', async () => {
        service.alterarStatus.mockRejectedValue(
            new ConflictError('Transição de status inválida', { de: 'ENTREGUE', para: 'PENDENTE' })
        );

        const res = await request(app).patch('/api/entregas/1/status').send({ status: 'PENDENTE' });

        expect(res.status).toBe(409);
        expect(res.body).toEqual({ error: 'Transição de status inválida', de: 'ENTREGUE', para: 'PENDENTE' });
        expect(service.alterarStatus).toHaveBeenCalledWith('1', 'PENDENTE');
    });

    it('PATCH /:id/status devolve a entrega atualizada', async () => {
        service.alterarStatus.mockResolvedValue({ id: 1, status: 'ENTREGUE' });

        const res = await request(app).patch('/api/entregas/1/status').send({ status: 'ENTREGUE' });

        expect(res.status).toBe(200);
        expect(res.body.status).toBe('ENTREGUE');
    });

    it('PATCH /:id/atribuir delega id e body', async () => {
        service.atribuir.mockResolvedValue({ id: 1 });

        const res = await request(app).patch('/api/entregas/1/atribuir').send({ veiculo_placa: 'ABC1234' });

        expect(res.status).toBe(200);
        expect(service.atribuir).toHaveBeenCalledWith('1', { veiculo_placa: 'ABC1234' });
    });

    it('DELETE /:id devolve mensagem e entrega', async () => {
        service.cancelar.mockResolvedValue({ id: 1, status: 'CANCELADA' });

        const res = await request(app).delete('/api/entregas/1');

        expect(res.status).toBe(200);
        expect(res.body).toEqual({ message: 'Entrega cancelada', entrega: { id: 1, status: 'CANCELADA' } });
    });

    it('erro inesperado vai para o error handler', async () => {
        service.listar.mockRejectedValue(new Error('db'));

        const res = await request(app).get('/api/entregas');

        expect(res.status).toBe(500);
        expect(res.body).toEqual({ error: 'interno' });
    });
});
