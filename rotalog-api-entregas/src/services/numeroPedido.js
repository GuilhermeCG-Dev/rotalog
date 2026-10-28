const { randomUUID } = require('crypto');

const gerarNumeroPedido = (data = new Date()) => {
    const dia = data.toISOString().slice(0, 10).replace(/-/g, '');
    const sufixo = randomUUID().replace(/-/g, '').slice(0, 8).toUpperCase();
    return `PED-${dia}-${sufixo}`;
};

module.exports = { gerarNumeroPedido };
