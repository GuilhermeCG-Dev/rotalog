package com.rotalog.exception;

public class TransicaoStatusInvalidaException extends RotalogException {

    public TransicaoStatusInvalidaException(String de, String para) {
        super("Transição de status inválida: " + de + " -> " + para);
    }
}
