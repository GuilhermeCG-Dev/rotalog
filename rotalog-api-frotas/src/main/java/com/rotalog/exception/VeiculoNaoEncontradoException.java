package com.rotalog.exception;

public class VeiculoNaoEncontradoException extends RotalogException {

    private VeiculoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static VeiculoNaoEncontradoException porId(Long id) {
        return new VeiculoNaoEncontradoException("Veículo não encontrado: " + id);
    }

    public static VeiculoNaoEncontradoException porPlaca(String placa) {
        return new VeiculoNaoEncontradoException("Veículo não encontrado com placa: " + placa);
    }
}
