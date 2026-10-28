const { calcularDistanciaKm, estimarTempoMinutos } = require('./distanciaService');

describe('distanciaService', () => {
    describe('calcularDistanciaKm', () => {
        it('calcula 1 grau de longitude no equador (coordenada 0 é válida)', () => {
            expect(calcularDistanciaKm({ lat: 0, lng: 0 }, { lat: 0, lng: 1 })).toBe(111.19);
        });

        it('retorna 0 para o mesmo ponto', () => {
            expect(calcularDistanciaKm({ lat: -23.55, lng: -46.63 }, { lat: -23.55, lng: -46.63 })).toBe(0);
        });

        it('calcula São Paulo -> Rio com Haversine', () => {
            const km = calcularDistanciaKm({ lat: -23.5505, lng: -46.6333 }, { lat: -22.9068, lng: -43.1729 });

            expect(km).toBeGreaterThan(350);
            expect(km).toBeLessThan(365);
        });

        it('aceita coordenadas como string numérica', () => {
            expect(calcularDistanciaKm({ lat: '0', lng: '0' }, { lat: '0', lng: '1' })).toBe(111.19);
        });

        it.each([null, undefined, '', 'abc'])('retorna null quando coordenada é %p', (valor) => {
            expect(calcularDistanciaKm({ lat: valor, lng: 0 }, { lat: 1, lng: 1 })).toBeNull();
        });
    });

    describe('estimarTempoMinutos', () => {
        const original = process.env.VELOCIDADE_MEDIA_KMH;

        afterEach(() => {
            if (original === undefined) delete process.env.VELOCIDADE_MEDIA_KMH;
            else process.env.VELOCIDADE_MEDIA_KMH = original;
        });

        it('usa 30 km/h por padrão (2 min/km), arredondando para cima', () => {
            delete process.env.VELOCIDADE_MEDIA_KMH;

            expect(estimarTempoMinutos(10)).toBe(20);
            expect(estimarTempoMinutos(10.1)).toBe(21);
        });

        it('lê a velocidade média do ambiente', () => {
            process.env.VELOCIDADE_MEDIA_KMH = '60';

            expect(estimarTempoMinutos(60)).toBe(60);
        });

        it('volta ao padrão quando a variável é inválida', () => {
            process.env.VELOCIDADE_MEDIA_KMH = 'abc';

            expect(estimarTempoMinutos(30)).toBe(60);
        });
    });
});
