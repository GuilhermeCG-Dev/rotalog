const { ValidationError, NotFoundError, ConflictError } = require('../errors');
const { EntregaStatus, statusValidos, podeTransitar } = require('./entregaStatus');

const opcional = (valor) => (valor === undefined || valor === '' ? null : valor);

const criarEntregaService = ({ entregaRepo, sequelize, distancia, gerarNumeroPedido, agora = () => new Date() }) => {
    const buscarOuFalhar = async (id) => {
        const entrega = await entregaRepo.buscarPorId(id);
        if (!entrega) throw new NotFoundError('Entrega não encontrada');
        return entrega;
    };

    const exigirTransicao = (entrega, para) => {
        if (!podeTransitar(entrega.status, para)) {
            throw new ConflictError('Transição de status inválida', { de: entrega.status, para });
        }
    };

    const salvarComEvento = (entrega, evento, descricao) => sequelize.transaction(async (transaction) => {
        await entregaRepo.salvar(entrega, { transaction });
        await entregaRepo.registrarEvento(entrega.id, evento, descricao, { transaction });
        return entrega;
    });

    const listar = (query = {}) => {
        const where = {};
        if (query.veiculo) where.veiculo_placa = query.veiculo;
        if (query.status) where.status = query.status.toUpperCase();
        if (query.motorista_id) where.motorista_id = query.motorista_id;
        return entregaRepo.listar(where);
    };

    const buscarPorId = async (id) => {
        const entrega = await entregaRepo.buscarComRastreamentos(id);
        if (!entrega) throw new NotFoundError('Entrega não encontrada');
        return entrega;
    };

    const buscarPorPedido = async (numeroPedido) => {
        const entrega = await entregaRepo.buscarPorPedido(numeroPedido);
        if (!entrega) throw new NotFoundError('Pedido não encontrado');
        return entrega;
    };

    const estatisticas = async () => {
        const porStatus = await entregaRepo.contarPorStatus();
        return {
            total: porStatus.reduce((acc, item) => acc + item.total, 0),
            por_status: porStatus,
            gerado_em: agora().toISOString()
        };
    };

    const criar = async (dados) => {
        const { origem_endereco, destino_endereco, peso_kg, observacoes,
                origem_lat, origem_lng, destino_lat, destino_lng } = dados;

        if (!origem_endereco || !destino_endereco) {
            throw new ValidationError('Endereços de origem e destino são obrigatórios');
        }

        const distanciaKm = distancia.calcularDistanciaKm(
            { lat: origem_lat, lng: origem_lng },
            { lat: destino_lat, lng: destino_lng }
        );
        const numeroPedido = gerarNumeroPedido(agora());

        return sequelize.transaction(async (transaction) => {
            const entrega = await entregaRepo.criar({
                numero_pedido: numeroPedido,
                origem_endereco,
                destino_endereco,
                origem_lat: opcional(origem_lat),
                origem_lng: opcional(origem_lng),
                destino_lat: opcional(destino_lat),
                destino_lng: opcional(destino_lng),
                peso_kg: opcional(peso_kg),
                distancia_km: distanciaKm,
                tempo_estimado_minutos: distanciaKm === null ? null : distancia.estimarTempoMinutos(distanciaKm),
                status: EntregaStatus.PENDENTE,
                observacoes: opcional(observacoes),
                data_criacao: agora(),
                data_atualizacao: agora()
            }, { transaction });

            await entregaRepo.registrarEvento(
                entrega.id, 'PEDIDO_CRIADO', `Pedido de entrega criado: ${numeroPedido}`, { transaction }
            );
            return entrega;
        });
    };

    const atualizar = async (id, dados) => {
        const entrega = await buscarOuFalhar(id);
        const { origem_endereco, destino_endereco, peso_kg, observacoes } = dados;

        if (origem_endereco) entrega.origem_endereco = origem_endereco;
        if (destino_endereco) entrega.destino_endereco = destino_endereco;
        if (peso_kg !== undefined) entrega.peso_kg = peso_kg;
        if (observacoes !== undefined) entrega.observacoes = observacoes;
        entrega.data_atualizacao = agora();

        await entregaRepo.salvar(entrega);
        return entrega;
    };

    const alterarStatus = async (id, status) => {
        const entrega = await buscarOuFalhar(id);

        if (!statusValidos.includes(status)) {
            throw new ValidationError('Status inválido', { statusValidos });
        }
        exigirTransicao(entrega, status);

        const statusAnterior = entrega.status;
        entrega.status = status;
        entrega.data_atualizacao = agora();
        if (status === EntregaStatus.EM_TRANSITO) entrega.data_coleta = agora();
        if (status === EntregaStatus.ENTREGUE) entrega.data_entrega = agora();

        return salvarComEvento(
            entrega, 'STATUS_ALTERADO', `Status alterado de ${statusAnterior} para ${status}`
        );
    };

    const atribuir = async (id, dados) => {
        const entrega = await buscarOuFalhar(id);
        const { veiculo_placa, motorista_id, motorista_nome, veiculo_modelo } = dados;

        if (!veiculo_placa || !motorista_id) {
            throw new ValidationError('Placa do veículo e ID do motorista são obrigatórios');
        }
        exigirTransicao(entrega, EntregaStatus.ATRIBUIDA);

        entrega.veiculo_placa = veiculo_placa;
        entrega.motorista_id = motorista_id;
        entrega.motorista_nome = opcional(motorista_nome);
        entrega.veiculo_modelo = opcional(veiculo_modelo);
        entrega.status = EntregaStatus.ATRIBUIDA;
        entrega.data_atualizacao = agora();

        return salvarComEvento(
            entrega,
            'ENTREGA_ATRIBUIDA',
            `Atribuída ao veículo ${veiculo_placa} e motorista #${motorista_id}`
        );
    };

    const cancelar = async (id) => {
        const entrega = await buscarOuFalhar(id);
        exigirTransicao(entrega, EntregaStatus.CANCELADA);

        entrega.status = EntregaStatus.CANCELADA;
        entrega.data_atualizacao = agora();

        return salvarComEvento(entrega, 'ENTREGA_CANCELADA', 'Entrega cancelada');
    };

    return {
        listar,
        buscarPorId,
        buscarPorPedido,
        estatisticas,
        criar,
        atualizar,
        alterarStatus,
        atribuir,
        cancelar
    };
};

module.exports = { criarEntregaService };
