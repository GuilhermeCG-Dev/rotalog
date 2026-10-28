package com.rotalog.controller;

import com.rotalog.domain.Veiculo;
import com.rotalog.exception.DadoVeiculoInvalidoException;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.PlacaInvalidaException;
import com.rotalog.exception.QuilometragemInvalidaException;
import com.rotalog.exception.StatusVeiculoInvalidoException;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.service.EstatisticasFrotaService;
import com.rotalog.service.VeiculoQuilometragemService;
import com.rotalog.service.VeiculoService;
import com.rotalog.service.VeiculoStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private Veiculo criarVeiculo() {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setModelo("Volvo FH");
        veiculo.setStatus("ATIVO");
        return veiculo;
    }

    @Test
    void buscarPorIdDeveRetornarVeiculoQuandoExiste() throws Exception {
        when(veiculoService.buscarPorId(1L)).thenReturn(criarVeiculo());

        mockMvc.perform(get("/veiculos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.placa").value("ABC1234"))
                .andExpect(jsonPath("$.modelo").value("Volvo FH"))
                .andExpect(jsonPath("$.status").value("ATIVO"));
    }

    @Test
    void buscarPorIdDeveRetornar404QuandoNaoExiste() throws Exception {
        when(veiculoService.buscarPorId(99L)).thenThrow(VeiculoNaoEncontradoException.porId(99L));

        mockMvc.perform(get("/veiculos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Veículo não encontrado: 99"));
    }

    @Test
    void listarTodosDeveRetornarLista() throws Exception {
        when(veiculoService.listarTodos()).thenReturn(List.of(criarVeiculo()));

        mockMvc.perform(get("/veiculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placa").value("ABC1234"));
    }

    @Test
    void buscarPorPlacaDeveRetornarVeiculoOu404() throws Exception {
        when(veiculoService.buscarPorPlaca("ABC1234")).thenReturn(criarVeiculo());
        when(veiculoService.buscarPorPlaca("ZZZ9999")).thenThrow(VeiculoNaoEncontradoException.porPlaca("ZZZ9999"));

        mockMvc.perform(get("/veiculos/placa/ABC1234")).andExpect(status().isOk());
        mockMvc.perform(get("/veiculos/placa/ZZZ9999")).andExpect(status().isNotFound());
    }

    @Test
    void listarPorStatusDeveRetornarListaOu400() throws Exception {
        when(veiculoService.obterVeiculosPorStatus("ativo")).thenReturn(List.of(criarVeiculo()));
        when(veiculoService.obterVeiculosPorStatus("outro")).thenThrow(new StatusVeiculoInvalidoException("outro"));

        mockMvc.perform(get("/veiculos/status/ativo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ATIVO"));
        mockMvc.perform(get("/veiculos/status/outro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status inválido: outro"));
    }

    @Test
    void registrarVeiculoDeveRetornar201() throws Exception {
        when(veiculoService.registrarVeiculo("ABC1234", "Volvo FH", 2020)).thenReturn(criarVeiculo());

        mockMvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ABC1234\",\"modelo\":\"Volvo FH\",\"anoFabricacao\":2020}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ABC1234"));
    }

    @Test
    void registrarVeiculoDeveRetornar409QuandoPlacaDuplicada() throws Exception {
        when(veiculoService.registrarVeiculo(any(), any(), any())).thenThrow(new PlacaDuplicadaException("ABC1234"));

        mockMvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ABC1234\",\"modelo\":\"Volvo FH\",\"anoFabricacao\":2020}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Veículo com placa ABC1234 já existe"));
    }

    @Test
    void registrarVeiculoDeveRetornar400QuandoDadosInvalidos() throws Exception {
        when(veiculoService.registrarVeiculo(eq("!!"), any(), any())).thenThrow(new PlacaInvalidaException("Placa deve ter 7 caracteres"));
        when(veiculoService.registrarVeiculo(eq("ABC1234"), eq(""), any()))
                .thenThrow(new DadoVeiculoInvalidoException("Modelo é obrigatório"));

        mockMvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON).content("{\"placa\":\"!!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Placa deve ter 7 caracteres"));
        mockMvc.perform(post("/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ABC1234\",\"modelo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Modelo é obrigatório"));
    }

    @Test
    void atualizarVeiculoDeveRetornar200Ou422() throws Exception {
        when(veiculoService.atualizarVeiculo(1L, "Novo", null, 500L)).thenReturn(criarVeiculo());
        when(veiculoService.atualizarVeiculo(1L, "Novo", null, 1L))
                .thenThrow(new QuilometragemInvalidaException("Quilometragem não pode ser menor que a atual"));

        mockMvc.perform(put("/veiculos/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelo\":\"Novo\",\"quilometragem\":500}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/veiculos/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelo\":\"Novo\",\"quilometragem\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void atualizarQuilometragemDeveRetornar200() throws Exception {
        when(veiculoQuilometragemService.atualizarQuilometragem(1L, 1000L)).thenReturn(criarVeiculo());

        mockMvc.perform(patch("/veiculos/1/quilometragem").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quilometragem\":1000}"))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarQuilometragemDeveRetornar400QuandoCampoAusente() throws Exception {
        mockMvc.perform(patch("/veiculos/1/quilometragem").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Campo 'quilometragem' é obrigatório"));
    }

    @Test
    void atualizarQuilometragemDeveRetornar422QuandoRegressao() throws Exception {
        when(veiculoQuilometragemService.atualizarQuilometragem(1L, 5L))
                .thenThrow(new QuilometragemInvalidaException("Quilometragem não pode ser menor que a atual"));

        mockMvc.perform(patch("/veiculos/1/quilometragem").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quilometragem\":5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Quilometragem não pode ser menor que a atual"));
    }

    @Test
    void desativarEReativarDevemRetornar200() throws Exception {
        when(veiculoStatusService.desativarVeiculo(1L)).thenReturn(criarVeiculo());
        when(veiculoStatusService.reativarVeiculo(1L)).thenReturn(criarVeiculo());

        mockMvc.perform(patch("/veiculos/1/desativar")).andExpect(status().isOk());
        mockMvc.perform(patch("/veiculos/1/reativar")).andExpect(status().isOk());
    }

    @Test
    void desativarEReativarDevemRetornar404QuandoNaoExiste() throws Exception {
        when(veiculoStatusService.desativarVeiculo(9L)).thenThrow(VeiculoNaoEncontradoException.porId(9L));
        when(veiculoStatusService.reativarVeiculo(9L)).thenThrow(VeiculoNaoEncontradoException.porId(9L));

        mockMvc.perform(patch("/veiculos/9/desativar")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/veiculos/9/reativar")).andExpect(status().isNotFound());
    }

    @Test
    void reativarDeveRetornar409QuandoEmManutencao() throws Exception {
        when(veiculoStatusService.reativarVeiculo(1L))
                .thenThrow(new TransicaoStatusInvalidaException("MANUTENCAO", "ATIVO"));

        mockMvc.perform(patch("/veiculos/1/reativar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Transição de status inválida: MANUTENCAO -> ATIVO"));
    }

    @Test
    void obterEstatisticasDeveRetornarCorpoDoService() throws Exception {
        when(estatisticasFrotaService.obterEstatisticas()).thenReturn("{\"total\": 1}");

        mockMvc.perform(get("/veiculos/estatisticas")).andExpect(status().isOk());
    }
}
