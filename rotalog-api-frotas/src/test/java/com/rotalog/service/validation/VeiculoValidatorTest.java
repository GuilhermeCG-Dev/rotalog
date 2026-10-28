package com.rotalog.service.validation;

import com.rotalog.exception.DadoVeiculoInvalidoException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VeiculoValidatorTest {

    private final VeiculoValidator validator = new VeiculoValidator();

    @Test
    void validarPlacaDeveLancarExcecaoQuandoNulaOuEmBranco() {
        for (String placa : new String[] {null, "", "   "}) {
            assertEquals("Placa é obrigatória",
                    assertThrows(PlacaInvalidaException.class, () -> validator.validarPlaca(placa)).getMessage());
        }
    }

    @Test
    void validarPlacaDeveLancarExcecaoQuandoTamanhoInvalido() {
        assertEquals("Placa deve ter 7 caracteres",
                assertThrows(PlacaInvalidaException.class, () -> validator.validarPlaca("ABC123")).getMessage());
        assertEquals("Placa deve ter 7 caracteres",
                assertThrows(PlacaInvalidaException.class, () -> validator.validarPlaca("ABC12345")).getMessage());
    }

    @Test
    void validarPlacaDeveLancarExcecaoQuandoFormatoInvalido() {
        for (String placa : new String[] {"!!!!!!!", "1234ABC", "ABCDEFG", "AB-1234", "ABC12'--"}) {
            assertThrows(PlacaInvalidaException.class, () -> validator.validarPlaca(placa));
        }
        assertEquals("Placa em formato inválido",
                assertThrows(PlacaInvalidaException.class, () -> validator.validarPlaca("!!!!!!!")).getMessage());
    }

    @Test
    void validarPlacaDeveAceitarFormatoAntigoEMercosulERetornarNormalizada() {
        assertEquals("ABC1234", validator.validarPlaca("ABC1234"));
        assertEquals("ABC1D23", validator.validarPlaca("abc1d23"));
        assertEquals("ABC1234", validator.validarPlaca("  abc1234 "));
    }

    @Test
    void normalizarPlacaDeveTratarNulo() {
        assertNull(validator.normalizarPlaca(null));
        assertEquals("ABC1234", validator.normalizarPlaca(" abc1234\t"));
    }

    @Test
    void validarModeloDeveLancarExcecaoQuandoNuloVazioOuEmBranco() {
        for (String modelo : new String[] {null, "", "  "}) {
            assertEquals("Modelo é obrigatório",
                    assertThrows(DadoVeiculoInvalidoException.class, () -> validator.validarModelo(modelo))
                            .getMessage());
        }
    }

    @Test
    void validarModeloDeveAceitarModeloPreenchido() {
        assertDoesNotThrow(() -> validator.validarModelo("Volvo FH"));
    }

    @Test
    void validarAnoFabricacaoDeveLancarExcecaoQuandoNuloOuForaDoIntervalo() {
        for (Integer ano : new Integer[] {null, 1899, 2101}) {
            assertEquals("Ano de fabricação inválido",
                    assertThrows(DadoVeiculoInvalidoException.class, () -> validator.validarAnoFabricacao(ano))
                            .getMessage());
        }
    }

    @Test
    void validarAnoFabricacaoDeveAceitarLimites() {
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(1900));
        assertDoesNotThrow(() -> validator.validarAnoFabricacao(2100));
    }

    @Test
    void validarQuilometragemDeveLancarExcecaoQuandoNulaOuNegativa() {
        assertEquals("Quilometragem é obrigatória",
                assertThrows(QuilometragemInvalidaException.class, () -> validator.validarQuilometragem(10L, null))
                        .getMessage());
        assertEquals("Quilometragem não pode ser negativa",
                assertThrows(QuilometragemInvalidaException.class, () -> validator.validarQuilometragem(10L, -1L))
                        .getMessage());
    }

    @Test
    void validarQuilometragemDeveRejeitarRegressao() {
        assertEquals("Quilometragem não pode ser menor que a atual",
                assertThrows(QuilometragemInvalidaException.class, () -> validator.validarQuilometragem(10L, 9L))
                        .getMessage());
    }

    @Test
    void validarQuilometragemDeveAceitarIgualMaiorOuAtualNulo() {
        assertDoesNotThrow(() -> validator.validarQuilometragem(10L, 10L));
        assertDoesNotThrow(() -> validator.validarQuilometragem(10L, 11L));
        assertDoesNotThrow(() -> validator.validarQuilometragem(null, 0L));
    }
}
