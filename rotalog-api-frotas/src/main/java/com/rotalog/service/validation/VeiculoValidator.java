package com.rotalog.service.validation;

import org.springframework.stereotype.Component;

/**
 * Regras de validação de entrada de veículo. Mensagens preservadas do contrato atual.
 */
@Component
public class VeiculoValidator {

    static final int TAMANHO_PLACA = 7;
    static final int ANO_MINIMO = 1900;
    static final int ANO_MAXIMO = 2100;

    public void validarPlaca(String placa) {
        if (placa == null || placa.isEmpty()) {
            throw new RuntimeException("Placa é obrigatória");
        }
        if (placa.length() != TAMANHO_PLACA) {
            throw new RuntimeException("Placa deve ter 7 caracteres");
        }
    }

    public void validarModelo(String modelo) {
        if (modelo == null || modelo.isEmpty()) {
            throw new RuntimeException("Modelo é obrigatório");
        }
    }

    public void validarAnoFabricacao(Integer anoFabricacao) {
        if (anoFabricacao == null || anoFabricacao < ANO_MINIMO || anoFabricacao > ANO_MAXIMO) {
            throw new RuntimeException("Ano de fabricação inválido");
        }
    }
}
