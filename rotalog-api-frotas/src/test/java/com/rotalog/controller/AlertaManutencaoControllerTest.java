package com.rotalog.controller;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.exception.StatusAlertaInvalidoException;
import com.rotalog.service.AlertaManutencaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertaManutencaoController.class)
class AlertaManutencaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertaManutencaoService alertaService;

    private AlertaManutencao alerta(StatusAlerta status) {
        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setId(1L);
        alerta.setVeiculoId(2L);
        alerta.setPlaca("KMA1B23");
        alerta.setMotivo(MotivoAlerta.QUILOMETRAGEM);
        alerta.setQuilometragem(85000L);
        alerta.setStatus(status);
        return alerta;
    }

    @Test
    void listarDeveRetornarAlertasFiltradosPorStatus() throws Exception {
        when(alertaService.listar("ENVIADO")).thenReturn(List.of(alerta(StatusAlerta.ENVIADO)));

        mockMvc.perform(get("/alertas").param("status", "ENVIADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value("KMA1B23"))
                .andExpect(jsonPath("$[0].status").value("ENVIADO"))
                .andExpect(jsonPath("$[0].motivo").value("QUILOMETRAGEM"));
    }

    @Test
    void listarComStatusInvalidoDeveRetornar400() throws Exception {
        when(alertaService.listar("XPTO")).thenThrow(new StatusAlertaInvalidoException("XPTO"));

        mockMvc.perform(get("/alertas").param("status", "XPTO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status de alerta inválido: XPTO"));
    }

    @Test
    void verificarDeveRetornarAlertasProcessados() throws Exception {
        when(alertaService.verificarEEnviar()).thenReturn(List.of(alerta(StatusAlerta.PENDENTE)));

        mockMvc.perform(post("/alertas/verificar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0].veiculoId").value(2));
    }
}
