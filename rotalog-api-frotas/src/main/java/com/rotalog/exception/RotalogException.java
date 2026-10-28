package com.rotalog.exception;

public abstract class RotalogException extends RuntimeException {

    protected RotalogException(String mensagem) {
        super(mensagem);
    }
}
