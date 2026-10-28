const { gerarNumeroPedido } = require('./numeroPedido');

describe('gerarNumeroPedido', () => {
    it('usa a data informada e sufixo de 8 caracteres', () => {
        const numero = gerarNumeroPedido(new Date('2026-10-27T12:00:00Z'));

        expect(numero).toMatch(/^PED-20261027-[0-9A-F]{8}$/);
    });

    it('usa a data atual por padrão', () => {
        const hoje = new Date().toISOString().slice(0, 10).replace(/-/g, '');

        expect(gerarNumeroPedido()).toContain(`PED-${hoje}-`);
    });

    it('gera números distintos', () => {
        const numeros = new Set(Array.from({ length: 200 }, () => gerarNumeroPedido()));

        expect(numeros.size).toBe(200);
    });
});
