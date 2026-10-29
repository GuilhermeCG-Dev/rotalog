package com.rotalog.exception;

public class StatusAlertaInvalidoException extends RotalogException {

    public StatusAlertaInvalidoException(String status) {
        super("Status de alerta inválido: " + status);
    }
}
