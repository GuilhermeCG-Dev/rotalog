const ORIGINAL_ENV = { ...process.env };

function loadMiddleware() {
    jest.resetModules();
    return require('./auth');
}

function mockRes() {
    const res = {};
    res.status = jest.fn().mockReturnValue(res);
    res.json = jest.fn().mockReturnValue(res);
    return res;
}

describe('authMiddleware', () => {
    let next;
    let logSpy;

    beforeEach(() => {
        next = jest.fn();
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
    });

    afterEach(() => {
        process.env = { ...ORIGINAL_ENV };
        logSpy.mockRestore();
    });

    describe('fora de produção', () => {
        it.each(['development', 'test', undefined])(
            'ignora a autenticação com NODE_ENV=%s',
            (env) => {
                if (env === undefined) {
                    delete process.env.NODE_ENV;
                } else {
                    process.env.NODE_ENV = env;
                }
                const authMiddleware = loadMiddleware();
                const req = { headers: {} };
                const res = mockRes();

                authMiddleware(req, res, next);

                expect(next).toHaveBeenCalledTimes(1);
                expect(res.status).not.toHaveBeenCalled();
                expect(req.user).toBeUndefined();
            }
        );
    });

    describe('em produção', () => {
        let authMiddleware;

        beforeEach(() => {
            process.env.NODE_ENV = 'production';
            authMiddleware = loadMiddleware();
        });

        it('retorna 401 quando o header authorization não existe', () => {
            const res = mockRes();

            authMiddleware({ headers: {} }, res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token não fornecido' });
            expect(next).not.toHaveBeenCalled();
        });

        it.each([
            ['só o token, sem esquema', 'tokenSemEsquema1234'],
            ['partes demais', 'Bearer token extra'],
            ['esquema diferente de Bearer', 'Basic abcdefghijkl'],
        ])('retorna 401 para header mal formatado: %s', (_desc, header) => {
            const res = mockRes();

            authMiddleware({ headers: { authorization: header } }, res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token mal formatado' });
            expect(next).not.toHaveBeenCalled();
        });

        it('retorna 401 para token com menos de 10 caracteres', () => {
            const res = mockRes();

            authMiddleware({ headers: { authorization: 'Bearer curto' } }, res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token inválido' });
            expect(next).not.toHaveBeenCalled();
        });

        it('retorna 401 para token vazio', () => {
            const res = mockRes();

            // "Bearer " gera duas partes: ['Bearer', '']
            authMiddleware({ headers: { authorization: 'Bearer ' } }, res, next);

            expect(res.status).toHaveBeenCalledWith(401);
            expect(res.json).toHaveBeenCalledWith({ error: 'Token inválido' });
            expect(next).not.toHaveBeenCalled();
        });

        it('aceita token com 10 caracteres, define req.user e chama next', () => {
            const req = { headers: { authorization: 'Bearer 1234567890' } };
            const res = mockRes();

            authMiddleware(req, res, next);

            expect(req.user).toEqual({ id: 1, nome: 'Admin', role: 'admin' });
            expect(next).toHaveBeenCalledTimes(1);
            expect(res.status).not.toHaveBeenCalled();
        });
    });

    describe('JWT_SECRET', () => {
        it('carrega o módulo com JWT_SECRET definido no ambiente', () => {
            process.env.JWT_SECRET = 'segredo-de-teste';
            process.env.NODE_ENV = 'test';

            expect(() => loadMiddleware()).not.toThrow();
        });
    });
});
