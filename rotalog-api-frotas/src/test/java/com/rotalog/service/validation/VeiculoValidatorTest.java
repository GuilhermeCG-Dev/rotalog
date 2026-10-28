package com.rotalog.service.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeiculoValidatorTest {

    private final VeiculoValidator validator = new VeiculoValidator();

    @Test
    void validarPlacaDeveLancarExcecaoQuandoNula() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarPlaca(null));
        assertEquals("Placa é obrigatória", ex.getMessage());
    }

    @Test
    void validarPlacaDeveLancarExcecaoQuandoVazia() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> validator.validarPlaca(""));
        assertEquals("Placa é obrigatória", ex.getMessage());
    }

    @Test
    void validarPlacaDeveLancarExcecaoQuandoTamanhoInvalido() {
        assertEquals("Placa deve ter 7 caracteres",
                assertThrows(RuntimeException.class, () -> validator.validarPlaca("ABC123")).getMessage());
        assertEquals("Placa deve ter 7 caracteres",
                assertThrows(RuntimeException.class, () -> validator.validarPlaca("ABC12345")).getMessage());
    }

    @Test
    void validarPlacaDeveAceitarSeteCaracteres() {
        assertDoesNotThrow(() -> validator.validarPlaca("ABC1234"));
        assertDoesNotThrow(() -> validator.validarPlaca("abc1d23"));
    }

    @Test
    void validarModeloDeveLancarExcecaoQuandoNuloOuVazio() {
        assertEquals("Modelo é obrigatório",
                assertThrows(RuntimeException.class, () -> validator.validarModelo(null)).getMessage());
        assertEquals("Modelo é obrigatório",
                assertThrows(RuntimeException.class, () -> validator.validarModelo("")).getMessage());
    }

    @Test
    void validarModeloDeveAceitarModeloPreenchido() {
        assertDoesNotThrow(() -> validator.validarModelo("Volvo FH"));
    }

    @Test
    void validarAnoFabricacaoDeveLancarExcecaoQuandoNuloOuForaDoIntervalo() {
        for (Integer ano : new Integer[] {null, 1899, 2101}) {
            assertEquals("Ano de fabricação inválido",
                    assertThrows(RuntimeException.class, () -> validator.validarAnoFabricacao(ano)).getMessage());
        }
    }

    @Test
    void validarAnoFabricacaoDeveAceitarLimites() {
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(1900));
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(2100));
    }
}
