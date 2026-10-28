const { criarEntregaService } = require('./entregaService');
const { ValidationError, NotFoundError, ConflictError } = require('../errors');

const AGORA = new Date('2026-10-27T12:00:00Z');

function criarEntrega(overrides = {}) {
    return { id: 1, status: 'PENDENTE', numero_pedido: 'PED-1', ...overrides };
}

describe('entregaService', () => {
    let repo;
    let sequelize;
    let distancia;
    let service;
    const transaction = { id: 'tx' };

    beforeEach(() => {
        repo = {
            listar: jest.fn(),
            buscarComRastreamentos: jest.fn(),
            buscarPorId: jest.fn(),
            buscarPorPedido: jest.fn(),
            criar: jest.fn(async (dados) => ({ id: 10, ...dados })),
            salvar: jest.fn().mockResolvedValue(undefined),
            registrarEvento: jest.fn().mockResolvedValue({}),
            contarPorStatus: jest.fn()
        };
        sequelize = { transaction: jest.fn((fn) => fn(transaction)) };
        distancia = {
            calcularDistanciaKm: jest.fn().mockReturnValue(null),
            estimarTempoMinutos: jest.fn().mockReturnValue(90)
        };
        service = criarEntregaService({
            entregaRepo: repo,
            sequelize,
            distancia,
            gerarNumeroPedido: jest.fn().mockReturnValue('PED-20261027-ABCD1234'),
            agora: () => AGORA
        });
    });

    describe('listar', () => {
        it('lista sem filtros', async () => {
            repo.listar.mockResolvedValue([]);

            await service.listar();

            expect(repo.listar).toHaveBeenCalledWith({});
        });

        it('aplica filtros de veículo, status e motorista', async () => {
            await service.listar({ veiculo: 'ABC1234', status: 'pendente', motorista_id: '7' });

            expect(repo.listar).toHaveBeenCalledWith({
                veiculo_placa: 'ABC1234',
                status: 'PENDENTE',
                motorista_id: '7'
            });
        });
    });

    describe('buscarPorId', () => {
        it('retorna a entrega', async () => {
            repo.buscarComRastreamentos.mockResolvedValue({ id: 1 });

            await expect(service.buscarPorId(1)).resolves.toEqual({ id: 1 });
        });

        it('lança NotFoundError quando não existe', async () => {
            repo.buscarComRastreamentos.mockResolvedValue(null);

            await expect(service.buscarPorId(1)).rejects.toThrow(NotFoundError);
        });
    });

    describe('buscarPorPedido', () => {
        it('retorna a entrega', async () => {
            repo.buscarPorPedido.mockResolvedValue({ id: 1 });

            await expect(service.buscarPorPedido('PED-1')).resolves.toEqual({ id: 1 });
        });

        it('lança NotFoundError com mensagem de pedido', async () => {
            repo.buscarPorPedido.mockResolvedValue(null);

            await expect(service.buscarPorPedido('X')).rejects.toThrow('Pedido não encontrado');
        });
    });

    describe('estatisticas', () => {
        it('soma o total e devolve números por status', async () => {
            repo.contarPorStatus.mockResolvedValue([
                { status: 'PENDENTE', total: 2 },
                { status: 'ENTREGUE', total: 3 }
            ]);

            const stats = await service.estatisticas();

            expect(stats.total).toBe(5);
            expect(stats.por_status).toHaveLength(2);
            expect(stats.gerado_em).toBe(AGORA.toISOString());
        });
    });

    describe('criar', () => {
        const base = { origem_endereco: 'Rua A', destino_endereco: 'Rua B' };

        it('lança ValidationError sem endereços', async () => {
            await expect(service.criar({ origem_endereco: 'Rua A' })).rejects.toThrow(ValidationError);
            expect(repo.criar).not.toHaveBeenCalled();
        });

        it('cria sem coordenadas e registra evento na transação', async () => {
            const entrega = await service.criar(base);

            expect(entrega).toMatchObject({
                numero_pedido: 'PED-20261027-ABCD1234',
                status: 'PENDENTE',
                distancia_km: null,
                tempo_estimado_minutos: null,
                peso_kg: null,
                observacoes: null,
                origem_lat: null
            });
            expect(repo.criar).toHaveBeenCalledWith(expect.any(Object), { transaction });
            expect(repo.registrarEvento).toHaveBeenCalledWith(
                10, 'PEDIDO_CRIADO', 'Pedido de entrega criado: PED-20261027-ABCD1234', { transaction }
            );
        });

        it('calcula distância e tempo e preserva coordenada 0 e peso 0', async () => {
            distancia.calcularDistanciaKm.mockReturnValue(111.19);

            const entrega = await service.criar({
                ...base,
                origem_lat: 0,
                origem_lng: 0,
                destino_lat: 0,
                destino_lng: 1,
                peso_kg: 0,
                observacoes: 'frágil'
            });

            expect(distancia.calcularDistanciaKm).toHaveBeenCalledWith({ lat: 0, lng: 0 }, { lat: 0, lng: 1 });
            expect(distancia.estimarTempoMinutos).toHaveBeenCalledWith(111.19);
            expect(entrega).toMatchObject({
                distancia_km: 111.19,
                tempo_estimado_minutos: 90,
                origem_lat: 0,
                peso_kg: 0,
                observacoes: 'frágil'
            });
        });

        it('trata string vazia como ausente', async () => {
            const entrega = await service.criar({ ...base, peso_kg: '' });

            expect(entrega.peso_kg).toBeNull();
        });
    });

    describe('atualizar', () => {
        it('lança NotFoundError quando não existe', async () => {
            repo.buscarPorId.mockResolvedValue(null);

            await expect(service.atualizar(1, {})).rejects.toThrow(NotFoundError);
        });

        it('atualiza os campos informados', async () => {
            const entrega = criarEntrega();
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.atualizar(1, { origem_endereco: 'X', destino_endereco: 'Y', peso_kg: 0, observacoes: '' });

            expect(entrega).toMatchObject({
                origem_endereco: 'X',
                destino_endereco: 'Y',
                peso_kg: 0,
                observacoes: '',
                data_atualizacao: AGORA
            });
            expect(repo.salvar).toHaveBeenCalledWith(entrega);
        });

        it('ignora campos ausentes', async () => {
            const entrega = criarEntrega({ origem_endereco: 'A', peso_kg: 5 });
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.atualizar(1, {});

            expect(entrega.origem_endereco).toBe('A');
            expect(entrega.peso_kg).toBe(5);
        });
    });

    describe('alterarStatus', () => {
        it('lança NotFoundError quando não existe', async () => {
            repo.buscarPorId.mockResolvedValue(null);

            await expect(service.alterarStatus(1, 'ATRIBUIDA')).rejects.toThrow(NotFoundError);
        });

        it('lança ValidationError para status desconhecido', async () => {
            repo.buscarPorId.mockResolvedValue(criarEntrega());

            await expect(service.alterarStatus(1, 'XPTO')).rejects.toMatchObject({
                status: 400,
                details: { statusValidos: expect.arrayContaining(['ENTREGUE']) }
            });
        });

        it('lança ConflictError em transição inválida', async () => {
            repo.buscarPorId.mockResolvedValue(criarEntrega({ status: 'ENTREGUE' }));

            await expect(service.alterarStatus(1, 'PENDENTE')).rejects.toMatchObject({
                status: 409,
                details: { de: 'ENTREGUE', para: 'PENDENTE' }
            });
            expect(repo.salvar).not.toHaveBeenCalled();
        });

        it('define data_coleta em EM_TRANSITO', async () => {
            const entrega = criarEntrega({ status: 'ATRIBUIDA' });
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.alterarStatus(1, 'EM_TRANSITO');

            expect(entrega.data_coleta).toBe(AGORA);
            expect(entrega.data_entrega).toBeUndefined();
            expect(repo.salvar).toHaveBeenCalledWith(entrega, { transaction });
            expect(repo.registrarEvento).toHaveBeenCalledWith(
                1, 'STATUS_ALTERADO', 'Status alterado de ATRIBUIDA para EM_TRANSITO', { transaction }
            );
        });

        it('define data_entrega em ENTREGUE', async () => {
            const entrega = criarEntrega({ status: 'EM_TRANSITO' });
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.alterarStatus(1, 'ENTREGUE');

            expect(entrega.data_entrega).toBe(AGORA);
            expect(entrega.data_coleta).toBeUndefined();
        });

        it('não define datas em CANCELADA', async () => {
            const entrega = criarEntrega();
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.alterarStatus(1, 'CANCELADA');

            expect(entrega.data_coleta).toBeUndefined();
            expect(entrega.data_entrega).toBeUndefined();
        });
    });

    describe('atribuir', () => {
        const dados = { veiculo_placa: 'ABC1234', motorista_id: 3, motorista_nome: 'João', veiculo_modelo: 'Fiorino' };

        it('lança NotFoundError quando não existe', async () => {
            repo.buscarPorId.mockResolvedValue(null);

            await expect(service.atribuir(1, dados)).rejects.toThrow(NotFoundError);
        });

        it('lança ValidationError sem placa ou motorista', async () => {
            repo.buscarPorId.mockResolvedValue(criarEntrega());

            await expect(service.atribuir(1, { veiculo_placa: 'ABC1234' })).rejects.toThrow(ValidationError);
        });

        it.each(['EM_TRANSITO', 'ENTREGUE', 'CANCELADA'])('bloqueia atribuição em %s', async (status) => {
            repo.buscarPorId.mockResolvedValue(criarEntrega({ status }));

            await expect(service.atribuir(1, dados)).rejects.toThrow(ConflictError);
        });

        it('atribui em PENDENTE', async () => {
            const entrega = criarEntrega();
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.atribuir(1, dados);

            expect(entrega).toMatchObject({
                veiculo_placa: 'ABC1234',
                motorista_id: 3,
                motorista_nome: 'João',
                veiculo_modelo: 'Fiorino',
                status: 'ATRIBUIDA'
            });
            expect(repo.registrarEvento).toHaveBeenCalledWith(
                1, 'ENTREGA_ATRIBUIDA', 'Atribuída ao veículo ABC1234 e motorista #3', { transaction }
            );
        });

        it('permite reatribuir em ATRIBUIDA e usa null para nome e modelo ausentes', async () => {
            const entrega = criarEntrega({ status: 'ATRIBUIDA' });
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.atribuir(1, { veiculo_placa: 'XYZ9876', motorista_id: 4 });

            expect(entrega.status).toBe('ATRIBUIDA');
            expect(entrega.motorista_nome).toBeNull();
            expect(entrega.veiculo_modelo).toBeNull();
        });
    });

    describe('cancelar', () => {
        it('lança NotFoundError quando não existe', async () => {
            repo.buscarPorId.mockResolvedValue(null);

            await expect(service.cancelar(1)).rejects.toThrow(NotFoundError);
            expect(repo.registrarEvento).not.toHaveBeenCalled();
        });

        it('lança ConflictError em entrega ENTREGUE', async () => {
            repo.buscarPorId.mockResolvedValue(criarEntrega({ status: 'ENTREGUE' }));

            await expect(service.cancelar(1)).rejects.toThrow(ConflictError);
        });

        it('cancela e registra evento', async () => {
            const entrega = criarEntrega();
            repo.buscarPorId.mockResolvedValue(entrega);

            await service.cancelar(1);

            expect(entrega.status).toBe('CANCELADA');
            expect(repo.registrarEvento).toHaveBeenCalledWith(
                1, 'ENTREGA_CANCELADA', 'Entrega cancelada', { transaction }
            );
        });
    });

    it('não registra evento quando o save falha dentro da transação', async () => {
        repo.buscarPorId.mockResolvedValue(criarEntrega());
        repo.salvar.mockRejectedValue(new Error('db'));

        await expect(service.cancelar(1)).rejects.toThrow('db');
        expect(repo.registrarEvento).not.toHaveBeenCalled();
    });

    it('usa o relógio real quando agora não é injetado', async () => {
        const real = criarEntregaService({
            entregaRepo: repo, sequelize, distancia, gerarNumeroPedido: () => 'PED'
        });
        repo.contarPorStatus.mockResolvedValue([]);

        const stats = await real.estatisticas();

        expect(new Date(stats.gerado_em).getTime()).toBeLessThanOrEqual(Date.now());
    });
});
