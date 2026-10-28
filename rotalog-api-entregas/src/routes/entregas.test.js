const request = require('supertest');
const express = require('express');

jest.mock('../models', () => ({
    Entrega: {
        findAll: jest.fn(),
        findByPk: jest.fn(),
        findOne: jest.fn(),
        create: jest.fn()
    },
    Rastreamento: {
        create: jest.fn()
    }
}));

jest.mock('../config/database', () => ({
    sequelize: {
        query: jest.fn(),
        constructor: { QueryTypes: { SELECT: 'SELECT' } }
    }
}));

const { Entrega, Rastreamento } = require('../models');
const { sequelize } = require('../config/database');
const entregasRouter = require('./entregas');

const app = express();
app.use(express.json());
app.use('/api/entregas', entregasRouter);

function mockEntrega(overrides = {}) {
    return {
        id: 1,
        numero_pedido: 'PED-1',
        status: 'PENDENTE',
        save: jest.fn().mockResolvedValue(undefined),
        toJSON() {
            const { save, toJSON, ...dados } = this;
            return dados;
        },
        ...overrides
    };
}

describe('rotas de entregas', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('GET /', () => {
        it('lista entregas sem filtros', async () => {
            Entrega.findAll.mockResolvedValue([{ id: 1 }]);

            const res = await request(app).get('/api/entregas');

            expect(res.status).toBe(200);
            expect(res.body).toEqual([{ id: 1 }]);
            expect(Entrega.findAll.mock.calls[0][0].where).toEqual({});
        });

        it('aplica filtros de veiculo, status e motorista', async () => {
            Entrega.findAll.mockResolvedValue([]);

            await request(app)
                .get('/api/entregas')
                .query({ veiculo: 'ABC1234', status: 'pendente', motorista_id: '7' });

            expect(Entrega.findAll.mock.calls[0][0].where).toEqual({
                veiculo_placa: 'ABC1234',
                status: 'PENDENTE',
                motorista_id: '7'
            });
        });

        it('retorna 500 quando o model falha', async () => {
            Entrega.findAll.mockRejectedValue(new Error('db'));

            const res = await request(app).get('/api/entregas');

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro interno do servidor' });
        });
    });

    describe('GET /stats', () => {
        it('retorna totais por status', async () => {
            sequelize.query.mockResolvedValue([
                { status: 'PENDENTE', total: '2' },
                { status: 'ENTREGUE', total: '3' }
            ]);

            const res = await request(app).get('/api/entregas/stats');

            expect(res.status).toBe(200);
            expect(res.body.total).toBe(5);
            expect(res.body.por_status).toHaveLength(2);
            expect(typeof res.body.gerado_em).toBe('string');
        });

        it('retorna 500 quando a query falha', async () => {
            sequelize.query.mockRejectedValue(new Error('db'));

            const res = await request(app).get('/api/entregas/stats');

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro ao buscar estatísticas' });
        });
    });

    describe('GET /:id', () => {
        it('retorna a entrega', async () => {
            Entrega.findByPk.mockResolvedValue({ id: 1 });

            const res = await request(app).get('/api/entregas/1');

            expect(res.status).toBe(200);
            expect(res.body).toEqual({ id: 1 });
        });

        it('retorna 404 quando não existe', async () => {
            Entrega.findByPk.mockResolvedValue(null);

            const res = await request(app).get('/api/entregas/99');

            expect(res.status).toBe(404);
            expect(res.body).toEqual({ error: 'Entrega não encontrada' });
        });

        it('retorna 500 quando o model falha', async () => {
            Entrega.findByPk.mockRejectedValue(new Error('db'));

            const res = await request(app).get('/api/entregas/1');

            expect(res.status).toBe(500);
        });
    });

    describe('GET /pedido/:numeroPedido', () => {
        it('retorna a entrega do pedido', async () => {
            Entrega.findOne.mockResolvedValue({ id: 1, numero_pedido: 'PED-1' });

            const res = await request(app).get('/api/entregas/pedido/PED-1');

            expect(res.status).toBe(200);
            expect(Entrega.findOne).toHaveBeenCalledWith({ where: { numero_pedido: 'PED-1' } });
        });

        it('retorna 404 quando o pedido não existe', async () => {
            Entrega.findOne.mockResolvedValue(null);

            const res = await request(app).get('/api/entregas/pedido/PED-X');

            expect(res.status).toBe(404);
            expect(res.body).toEqual({ error: 'Pedido não encontrado' });
        });

        it('retorna 500 quando o model falha', async () => {
            Entrega.findOne.mockRejectedValue(new Error('db'));

            const res = await request(app).get('/api/entregas/pedido/PED-1');

            expect(res.status).toBe(500);
        });
    });

    describe('POST /', () => {
        const base = { origem_endereco: 'Rua A', destino_endereco: 'Rua B' };

        beforeEach(() => {
            Entrega.create.mockImplementation(async (dados) => ({ id: 10, ...dados }));
            Rastreamento.create.mockResolvedValue({});
        });

        it('retorna 400 sem endereços', async () => {
            const res = await request(app).post('/api/entregas').send({ origem_endereco: 'Rua A' });

            expect(res.status).toBe(400);
            expect(Entrega.create).not.toHaveBeenCalled();
        });

        it('cria entrega sem coordenadas', async () => {
            const res = await request(app).post('/api/entregas').send(base);

            expect(res.status).toBe(201);
            expect(res.body.status).toBe('PENDENTE');
            expect(res.body.numero_pedido).toMatch(/^PED-\d+-\d+$/);
            expect(res.body.distancia_km).toBeNull();
            expect(res.body.tempo_estimado_minutos).toBeNull();
            expect(res.body.peso_kg).toBeNull();
            expect(res.body.observacoes).toBeNull();
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ entrega_id: 10, evento: 'PEDIDO_CRIADO' })
            );
        });

        it('calcula distância e tempo com coordenadas', async () => {
            const res = await request(app).post('/api/entregas').send({
                ...base,
                origem_lat: 0,
                origem_lng: 0,
                destino_lat: 3,
                destino_lng: 4,
                peso_kg: 12,
                observacoes: 'frágil'
            });

            expect(res.status).toBe(201);
            // coordenada 0 é falsy: cai no ramo sem distância
            expect(res.body.distancia_km).toBeNull();

            const res2 = await request(app).post('/api/entregas').send({
                ...base,
                origem_lat: 1,
                origem_lng: 1,
                destino_lat: 4,
                destino_lng: 5,
                peso_kg: 12,
                observacoes: 'frágil'
            });

            expect(res2.body.distancia_km).toBeCloseTo(5 * 111);
            expect(res2.body.tempo_estimado_minutos).toBe(Math.ceil(5 * 111 * 2));
            expect(res2.body.peso_kg).toBe(12);
            expect(res2.body.observacoes).toBe('frágil');
        });

        it('retorna 500 com detalhes quando o model falha', async () => {
            Entrega.create.mockRejectedValue(new Error('boom'));

            const res = await request(app).post('/api/entregas').send(base);

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro ao criar entrega', detalhes: 'boom' });
        });
    });

    describe('PUT /:id', () => {
        it('atualiza os campos informados', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);

            const res = await request(app).put('/api/entregas/1').send({
                origem_endereco: 'X',
                destino_endereco: 'Y',
                peso_kg: 0,
                observacoes: ''
            });

            expect(res.status).toBe(200);
            expect(entrega.origem_endereco).toBe('X');
            expect(entrega.destino_endereco).toBe('Y');
            expect(entrega.peso_kg).toBe(0);
            expect(entrega.observacoes).toBe('');
            expect(entrega.save).toHaveBeenCalled();
        });

        it('ignora campos ausentes', async () => {
            const entrega = mockEntrega({ origem_endereco: 'A', peso_kg: 5 });
            Entrega.findByPk.mockResolvedValue(entrega);

            const res = await request(app).put('/api/entregas/1').send({});

            expect(res.status).toBe(200);
            expect(entrega.origem_endereco).toBe('A');
            expect(entrega.peso_kg).toBe(5);
        });

        it('retorna 404 quando não existe', async () => {
            Entrega.findByPk.mockResolvedValue(null);

            const res = await request(app).put('/api/entregas/1').send({});

            expect(res.status).toBe(404);
        });

        it('retorna 500 quando o save falha', async () => {
            const entrega = mockEntrega({ save: jest.fn().mockRejectedValue(new Error('db')) });
            Entrega.findByPk.mockResolvedValue(entrega);

            const res = await request(app).put('/api/entregas/1').send({});

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro ao atualizar entrega' });
        });
    });

    describe('PATCH /:id/status', () => {
        it('retorna 404 quando não existe', async () => {
            Entrega.findByPk.mockResolvedValue(null);

            const res = await request(app).patch('/api/entregas/1/status').send({ status: 'ENTREGUE' });

            expect(res.status).toBe(404);
        });

        it('retorna 400 para status inválido', async () => {
            Entrega.findByPk.mockResolvedValue(mockEntrega());

            const res = await request(app).patch('/api/entregas/1/status').send({ status: 'XPTO' });

            expect(res.status).toBe(400);
            expect(res.body.statusValidos).toContain('ENTREGUE');
        });

        it('define data_coleta em EM_TRANSITO', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            const res = await request(app).patch('/api/entregas/1/status').send({ status: 'EM_TRANSITO' });

            expect(res.status).toBe(200);
            expect(entrega.data_coleta).toBeInstanceOf(Date);
            expect(entrega.data_entrega).toBeUndefined();
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    evento: 'STATUS_ALTERADO',
                    descricao: 'Status alterado de PENDENTE para EM_TRANSITO'
                })
            );
        });

        it('define data_entrega em ENTREGUE', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            await request(app).patch('/api/entregas/1/status').send({ status: 'ENTREGUE' });

            expect(entrega.data_entrega).toBeInstanceOf(Date);
            expect(entrega.data_coleta).toBeUndefined();
        });

        it('não define datas para outros status', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            await request(app).patch('/api/entregas/1/status').send({ status: 'CANCELADA' });

            expect(entrega.data_coleta).toBeUndefined();
            expect(entrega.data_entrega).toBeUndefined();
        });

        it('retorna 500 quando o model falha', async () => {
            Entrega.findByPk.mockRejectedValue(new Error('db'));

            const res = await request(app).patch('/api/entregas/1/status').send({ status: 'ENTREGUE' });

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro ao atualizar status' });
        });
    });

    describe('PATCH /:id/atribuir', () => {
        const dados = {
            veiculo_placa: 'ABC1234',
            motorista_id: 3,
            motorista_nome: 'João',
            veiculo_modelo: 'Fiorino'
        };

        it('retorna 404 quando não existe', async () => {
            Entrega.findByPk.mockResolvedValue(null);

            const res = await request(app).patch('/api/entregas/1/atribuir').send(dados);

            expect(res.status).toBe(404);
        });

        it('retorna 400 sem placa ou motorista', async () => {
            Entrega.findByPk.mockResolvedValue(mockEntrega());

            const res = await request(app).patch('/api/entregas/1/atribuir').send({ veiculo_placa: 'ABC1234' });

            expect(res.status).toBe(400);
        });

        it('atribui veículo e motorista', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            const res = await request(app).patch('/api/entregas/1/atribuir').send(dados);

            expect(res.status).toBe(200);
            expect(entrega.status).toBe('ATRIBUIDA');
            expect(entrega.motorista_nome).toBe('João');
            expect(entrega.veiculo_modelo).toBe('Fiorino');
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ evento: 'ENTREGA_ATRIBUIDA' })
            );
        });

        it('usa null para nome e modelo ausentes', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            await request(app)
                .patch('/api/entregas/1/atribuir')
                .send({ veiculo_placa: 'ABC1234', motorista_id: 3 });

            expect(entrega.motorista_nome).toBeNull();
            expect(entrega.veiculo_modelo).toBeNull();
        });

        it('retorna 500 quando o model falha', async () => {
            Entrega.findByPk.mockRejectedValue(new Error('db'));

            const res = await request(app).patch('/api/entregas/1/atribuir').send(dados);

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro ao atribuir entrega' });
        });
    });

    describe('DELETE /:id', () => {
        it('cancela a entrega', async () => {
            const entrega = mockEntrega();
            Entrega.findByPk.mockResolvedValue(entrega);
            Rastreamento.create.mockResolvedValue({});

            const res = await request(app).delete('/api/entregas/1');

            expect(res.status).toBe(200);
            expect(res.body.message).toBe('Entrega cancelada');
            expect(entrega.status).toBe('CANCELADA');
            expect(Rastreamento.create).toHaveBeenCalledWith(
                expect.objectContaining({ evento: 'ENTREGA_CANCELADA' })
            );
        });

        it('retorna 404 quando não existe', async () => {
            Entrega.findByPk.mockResolvedValue(null);

            const res = await request(app).delete('/api/entregas/1');

            expect(res.status).toBe(404);
            expect(Rastreamento.create).not.toHaveBeenCalled();
        });

        it('retorna 500 quando o save falha', async () => {
            Entrega.findByPk.mockResolvedValue(
                mockEntrega({ save: jest.fn().mockRejectedValue(new Error('db')) })
            );

            const res = await request(app).delete('/api/entregas/1');

            expect(res.status).toBe(500);
            expect(res.body).toEqual({ error: 'Erro ao cancelar entrega' });
        });
    });
});
