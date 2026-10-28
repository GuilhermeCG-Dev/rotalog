class AppError extends Error {
    constructor(message, status = 500, details = {}) {
        super(message);
        this.name = this.constructor.name;
        this.status = status;
        this.details = details;
    }
}

class ValidationError extends AppError {
    constructor(message, details) {
        super(message, 400, details);
    }
}

class NotFoundError extends AppError {
    constructor(message, details) {
        super(message, 404, details);
    }
}

class ConflictError extends AppError {
    constructor(message, details) {
        super(message, 409, details);
    }
}

module.exports = { AppError, ValidationError, NotFoundError, ConflictError };
