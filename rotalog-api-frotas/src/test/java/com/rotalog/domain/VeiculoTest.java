package com.rotalog.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeiculoTest {

    @Test
    void aoCriarDevePreencherDatasDeCadastroEAtualizacao() {
        Veiculo veiculo = new Veiculo();

        veiculo.aoCriar();

        assertNotNull(veiculo.getDataCadastro());
        assertEquals(veiculo.getDataCadastro(), veiculo.getDataAtualizacao());
    }

    @Test
    void aoAtualizarDeveRenovarApenasDataAtualizacao() {
        Veiculo veiculo = new Veiculo();
        LocalDateTime antiga = LocalDateTime.now().minusDays(1);
        veiculo.setDataCadastro(antiga);
        veiculo.setDataAtualizacao(antiga);

        veiculo.aoAtualizar();

        assertEquals(antiga, veiculo.getDataCadastro());
        assertTrue(veiculo.getDataAtualizacao().isAfter(antiga));
    }
}
