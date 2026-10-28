const { AppError } = require('../errors');

const asyncHandler = (handler) => async (req, res, next) => {
    try {
        await handler(req, res, next);
    } catch (err) {
        if (err instanceof AppError) {
            return res.status(err.status).json({ error: err.message, ...err.details });
        }
        next(err);
    }
};

module.exports = asyncHandler;
