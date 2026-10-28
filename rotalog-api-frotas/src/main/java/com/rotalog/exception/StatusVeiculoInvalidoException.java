package com.rotalog.exception;

public class StatusVeiculoInvalidoException extends RotalogException {

    public StatusVeiculoInvalidoException(String status) {
        super("Status inválido: " + status);
    }
}
