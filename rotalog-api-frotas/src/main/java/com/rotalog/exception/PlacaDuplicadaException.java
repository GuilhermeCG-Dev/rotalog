package com.rotalog.exception;

public class PlacaDuplicadaException extends RotalogException {

    public PlacaDuplicadaException(String placa) {
        super("Veículo com placa " + placa + " já existe");
    }
}
