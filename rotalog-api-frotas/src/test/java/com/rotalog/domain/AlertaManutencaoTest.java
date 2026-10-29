package com.rotalog.domain;

import com.rotalog.dto.AlertaManutencaoResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertaManutencaoTest {

    @Test
    void aoCriarDeveDefinirDatasDeCriacaoEAtualizacaoIguais() {
        AlertaManutencao alerta = new AlertaManutencao();

        alerta.aoCriar();

        assertNotNull(alerta.getDataCriacao());
        assertEquals(alerta.getDataCriacao(), alerta.getDataAtualizacao());
    }

    @Test
    void aoAtualizarDeveRenovarApenasDataDeAtualizacao() {
        AlertaManutencao alerta = new AlertaManutencao();
        LocalDateTime antiga = LocalDateTime.now().minusDays(1);
        alerta.setDataCriacao(antiga);
        alerta.setDataAtualizacao(antiga);

        alerta.aoAtualizar();

        assertEquals(antiga, alerta.getDataCriacao());
        assertTrue(alerta.getDataAtualizacao().isAfter(antiga));
    }

    @Test
    void responseDeveCopiarCamposDoAlertaEConverterEnumsParaTexto() {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setId(1L);
        alerta.setVeiculoId(2L);
        alerta.setPlaca("KMA1B23");
        alerta.setMotivo(MotivoAlerta.QUILOMETRAGEM_E_TEMPO);
        alerta.setQuilometragem(85000L);
        alerta.setStatus(StatusAlerta.FALHA);
        alerta.setNotificacaoId(9L);
        alerta.setErroMensagem("SMTP timeout");

        AlertaManutencaoResponse response = AlertaManutencaoResponse.de(alerta);

        assertEquals(1L, response.getId());
        assertEquals(2L, response.getVeiculoId());
        assertEquals("KMA1B23", response.getPlaca());
        assertEquals("QUILOMETRAGEM_E_TEMPO", response.getMotivo());
        assertEquals(85000L, response.getQuilometragem());
        assertEquals("FALHA", response.getStatus());
        assertEquals(9L, response.getNotificacaoId());
        assertEquals("SMTP timeout", response.getErroMensagem());
        assertNull(response.getDataUltimaManutencao());
    }
}
