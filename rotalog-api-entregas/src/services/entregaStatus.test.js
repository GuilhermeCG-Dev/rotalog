const { EntregaStatus, statusValidos, podeTransitar } = require('./entregaStatus');

describe('entregaStatus', () => {
    it('lista os 5 status válidos', () => {
        expect(statusValidos).toEqual(['PENDENTE', 'ATRIBUIDA', 'EM_TRANSITO', 'ENTREGUE', 'CANCELADA']);
    });

    const permitidas = [
        ['PENDENTE', 'ATRIBUIDA'],
        ['PENDENTE', 'CANCELADA'],
        ['ATRIBUIDA', 'ATRIBUIDA'],
        ['ATRIBUIDA', 'EM_TRANSITO'],
        ['ATRIBUIDA', 'CANCELADA'],
        ['EM_TRANSITO', 'ENTREGUE'],
        ['EM_TRANSITO', 'CANCELADA']
    ];

    it.each(permitidas)('permite %s -> %s', (de, para) => {
        expect(podeTransitar(de, para)).toBe(true);
    });

    it('bloqueia todas as demais combinações', () => {
        const chave = ([de, para]) => `${de}>${para}`;
        const permitidasSet = new Set(permitidas.map(chave));

        for (const de of statusValidos) {
            for (const para of statusValidos) {
                expect(podeTransitar(de, para)).toBe(permitidasSet.has(chave([de, para])));
            }
        }
    });

    it('trata ENTREGUE e CANCELADA como terminais', () => {
        expect(statusValidos.some((s) => podeTransitar(EntregaStatus.ENTREGUE, s))).toBe(false);
        expect(statusValidos.some((s) => podeTransitar(EntregaStatus.CANCELADA, s))).toBe(false);
    });

    it('rejeita status de origem desconhecido', () => {
        expect(podeTransitar('XPTO', 'ATRIBUIDA')).toBe(false);
    });
});
