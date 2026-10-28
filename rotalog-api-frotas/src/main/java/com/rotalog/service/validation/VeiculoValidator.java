package com.rotalog.service.validation;

import com.rotalog.exception.DadoVeiculoInvalidoException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de validação de entrada de veículo. Mensagens preservadas do contrato atual.
 */
@Component
public class VeiculoValidator {

    static final int TAMANHO_PLACA = 7;
    static final int ANO_MINIMO = 1900;
    static final int ANO_MAXIMO = 2100;

    // Formato antigo (AAA9999) e Mercosul (AAA9A99)
    private static final Pattern FORMATO_PLACA = Pattern.compile("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$");

    /**
     * Remove espaços e coloca em maiúsculas, para que a checagem de duplicidade
     * e o valor gravado usem sempre a mesma representação.
     */
    public String normalizarPlaca(String placa) {
        return placa == null ? null : placa.trim().toUpperCase(Locale.ROOT);
    }

    /** Valida e devolve a placa normalizada. */
    public String validarPlaca(String placa) {
        String normalizada = normalizarPlaca(placa);
        if (normalizada == null || normalizada.isEmpty()) {
            throw new PlacaInvalidaException("Placa é obrigatória");
        }
        if (normalizada.length() != TAMANHO_PLACA) {
            throw new PlacaInvalidaException("Placa deve ter 7 caracteres");
        }
        if (!FORMATO_PLACA.matcher(normalizada).matches()) {
            throw new PlacaInvalidaException("Placa em formato inválido");
        }
        return normalizada;
    }

    public void validarModelo(String modelo) {
        if (modelo == null || modelo.trim().isEmpty()) {
            throw new DadoVeiculoInvalidoException("Modelo é obrigatório");
        }
    }

    public void validarAnoFabricacao(Integer anoFabricacao) {
        if (anoFabricacao == null || anoFabricacao < ANO_MINIMO || anoFabricacao > ANO_MAXIMO) {
            throw new DadoVeiculoInvalidoException("Ano de fabricação inválido");
        }
    }

    /**
     * O odômetro nunca regride. {@code atual} pode ser nulo (coluna nullable).
     */
    public void validarQuilometragem(Long atual, Long nova) {
        if (nova == null) {
            throw new QuilometragemInvalidaException("Quilometragem é obrigatória");
        }
        if (nova < 0) {
            throw new QuilometragemInvalidaException("Quilometragem não pode ser negativa");
        }
        if (atual != null && nova < atual) {
            throw new QuilometragemInvalidaException("Quilometragem não pode ser menor que a atual");
        }
    }
}
