const EntregaStatus = Object.freeze({
    PENDENTE: 'PENDENTE',
    ATRIBUIDA: 'ATRIBUIDA',
    EM_TRANSITO: 'EM_TRANSITO',
    ENTREGUE: 'ENTREGUE',
    CANCELADA: 'CANCELADA'
});

// ENTREGUE e CANCELADA são terminais; ATRIBUIDA -> ATRIBUIDA é reatribuição.
const TRANSICOES = Object.freeze({
    [EntregaStatus.PENDENTE]: [EntregaStatus.ATRIBUIDA, EntregaStatus.CANCELADA],
    [EntregaStatus.ATRIBUIDA]: [EntregaStatus.ATRIBUIDA, EntregaStatus.EM_TRANSITO, EntregaStatus.CANCELADA],
    [EntregaStatus.EM_TRANSITO]: [EntregaStatus.ENTREGUE, EntregaStatus.CANCELADA],
    [EntregaStatus.ENTREGUE]: [],
    [EntregaStatus.CANCELADA]: []
});

const statusValidos = Object.values(EntregaStatus);

const podeTransitar = (de, para) => (TRANSICOES[de] || []).includes(para);

module.exports = { EntregaStatus, statusValidos, podeTransitar };
