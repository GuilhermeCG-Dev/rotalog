package com.rotalog.controller;

import com.rotalog.domain.Veiculo;
import com.rotalog.service.EstatisticasFrotaService;
import com.rotalog.service.VeiculoQuilometragemService;
import com.rotalog.service.VeiculoService;
import com.rotalog.service.VeiculoStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VeiculoController.class)
class VeiculoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VeiculoService veiculoService;

    @MockBean
    private VeiculoStatusService veiculoStatusService;

    @MockBean
    private VeiculoQuilometragemService veiculoQuilometragemService;

    @MockBean
    private EstatisticasFrotaService estatisticasFrotaService;

    @Test
    void buscarPorIdDeveRetornarVeiculoQuandoExiste() throws Exception {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setModelo("Volvo FH");
        veiculo.setStatus("ATIVO");

        when(veiculoService.buscarPorId(1L)).thenReturn(veiculo);

        mockMvc.perform(get("/veiculos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.placa").value("ABC1234"))
                .andExpect(jsonPath("$.modelo").value("Volvo FH"))
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void buscarPorIdDeveRetornar404QuandoNaoExiste() throws Exception {
        when(veiculoService.buscarPorId(99L))
                .thenThrow(new RuntimeException("Veículo não encontrado: 99"));

        mockMvc.perform(get("/veiculos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Veículo não encontrado: 99"));
    }
}
