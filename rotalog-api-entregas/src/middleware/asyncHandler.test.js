const asyncHandler = require('./asyncHandler');
const { AppError, ValidationError, NotFoundError, ConflictError } = require('../errors');

function mockRes() {
    const res = {};
    res.status = jest.fn().mockReturnValue(res);
    res.json = jest.fn().mockReturnValue(res);
    return res;
}

describe('asyncHandler', () => {
    it('executa o handler sem interferir no sucesso', async () => {
        const handler = jest.fn().mockResolvedValue(undefined);
        const next = jest.fn();

        await asyncHandler(handler)({}, mockRes(), next);

        expect(handler).toHaveBeenCalled();
        expect(next).not.toHaveBeenCalled();
    });

    it('responde AppError com status e detalhes', async () => {
        const res = mockRes();
        const handler = async () => { throw new ConflictError('conflito', { de: 'A', para: 'B' }); };

        await asyncHandler(handler)({}, res, jest.fn());

        expect(res.status).toHaveBeenCalledWith(409);
        expect(res.json).toHaveBeenCalledWith({ error: 'conflito', de: 'A', para: 'B' });
    });

    it('encaminha erro inesperado para next', async () => {
        const erro = new Error('boom');
        const next = jest.fn();

        await asyncHandler(async () => { throw erro; })({}, mockRes(), next);

        expect(next).toHaveBeenCalledWith(erro);
    });

    it('erros tipados carregam status esperado', () => {
        expect(new AppError('x').status).toBe(500);
        expect(new AppError('x').details).toEqual({});
        expect(new ValidationError('x').status).toBe(400);
        expect(new NotFoundError('x').status).toBe(404);
        expect(new ConflictError('x').status).toBe(409);
        expect(new NotFoundError('x').name).toBe('NotFoundError');
    });
});
