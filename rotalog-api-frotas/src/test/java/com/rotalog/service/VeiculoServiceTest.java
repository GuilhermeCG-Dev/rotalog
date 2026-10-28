package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import com.rotalog.service.validation.VeiculoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoServiceTest {

    private static final String PLACA = "ABC1234";

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private VeiculoNotificador notificador;

    private VeiculoService veiculoService;

    @BeforeEach
    void setUp() {
        veiculoService = new VeiculoService(veiculoRepository, new VeiculoValidator(), notificador);
    }

    private Veiculo criarVeiculo(Long id, String placa, Long quilometragem) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo("Volvo FH");
        veiculo.setAnoFabricacao(2020);
        veiculo.setStatus("ATIVO");
        veiculo.setQuilometragem(quilometragem);
        return veiculo;
    }

    private void mockSaveRetornaArgumento() {
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // listarTodos / buscar

    @Test
    void listarTodosDeveRetornarListaDoRepositorio() {
        List<Veiculo> lista = List.of(criarVeiculo(1L, PLACA, 0L));
        when(veiculoRepository.findAll()).thenReturn(lista);

        assertSame(lista, veiculoService.listarTodos());
    }

    @Test
    void buscarPorIdDeveRetornarVeiculoQuandoExiste() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 0L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        assertSame(veiculo, veiculoService.buscarPorId(1L));
    }

    @Test
    void buscarPorIdDeveLancarExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> veiculoService.buscarPorId(99L));
        assertEquals("Veículo não encontrado: 99", ex.getMessage());
    }

    @Test
    void buscarPorPlacaDeveRetornarVeiculoQuandoExiste() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 0L);
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.of(veiculo));

        assertSame(veiculo, veiculoService.buscarPorPlaca(PLACA));
    }

    @Test
    void buscarPorPlacaDeveLancarExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> veiculoService.buscarPorPlaca(PLACA));
        assertEquals("Veículo não encontrado com placa: " + PLACA, ex.getMessage());
    }

    // registrarVeiculo

    @Test
    void registrarVeiculoDeveSalvarENotificarQuandoDadosValidos() {
        when(veiculoRepository.findByPlaca("abc1234")).thenReturn(Optional.empty());
        mockSaveRetornaArgumento();

        Veiculo salvo = veiculoService.registrarVeiculo("abc1234", "Scania R450", 2021);

        assertEquals("ABC1234", salvo.getPlaca());
        assertEquals("Scania R450", salvo.getModelo());
        assertEquals(2021, salvo.getAnoFabricacao());
        assertEquals("ATIVO", salvo.getStatus());
        assertEquals(0L, salvo.getQuilometragem());
        verify(notificador).notificarNovoVeiculo(salvo);
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoPlacaNula() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo(null, "Volvo FH", 2020));
        assertEquals("Placa é obrigatória", ex.getMessage());
        verifyNoInteractions(veiculoRepository, notificador);
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoPlacaVazia() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo("", "Volvo FH", 2020));
        assertEquals("Placa é obrigatória", ex.getMessage());
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoPlacaTamanhoInvalido() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo("ABC123", "Volvo FH", 2020));
        assertEquals("Placa deve ter 7 caracteres", ex.getMessage());
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoPlacaDuplicada() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.of(criarVeiculo(1L, PLACA, 0L)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo(PLACA, "Volvo FH", 2020));
        assertEquals("Veículo com placa " + PLACA + " já existe", ex.getMessage());
        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoModeloNulo() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo(PLACA, null, 2020));
        assertEquals("Modelo é obrigatório", ex.getMessage());
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoModeloVazio() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo(PLACA, "", 2020));
        assertEquals("Modelo é obrigatório", ex.getMessage());
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoAnoNulo() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.registrarVeiculo(PLACA, "Volvo FH", null));
        assertEquals("Ano de fabricação inválido", ex.getMessage());
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoAnoMenorQue1900() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo(PLACA, "Volvo FH", 1899));
    }

    @Test
    void registrarVeiculoDeveLancarExcecaoQuandoAnoMaiorQue2100() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.registrarVeiculo(PLACA, "Volvo FH", 2101));
    }

    @Test
    void registrarVeiculoDeveAceitarAnosLimite() {
        when(veiculoRepository.findByPlaca(PLACA)).thenReturn(Optional.empty());
        mockSaveRetornaArgumento();

        assertEquals(1900, veiculoService.registrarVeiculo(PLACA, "Volvo FH", 1900).getAnoFabricacao());
        assertEquals(2100, veiculoService.registrarVeiculo(PLACA, "Volvo FH", 2100).getAnoFabricacao());
    }

    // atualizarVeiculo

    @Test
    void atualizarVeiculoDeveAtualizarTodosOsCampos() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 1000L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        mockSaveRetornaArgumento();

        Veiculo atualizado = veiculoService.atualizarVeiculo(1L, "Novo Modelo", 2022, 2000L);

        assertEquals("Novo Modelo", atualizado.getModelo());
        assertEquals(2022, atualizado.getAnoFabricacao());
        assertEquals(2000L, atualizado.getQuilometragem());
    }

    @Test
    void atualizarVeiculoDeveManterCamposQuandoParametrosNulosOuVazios() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 1000L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        mockSaveRetornaArgumento();

        Veiculo atualizado = veiculoService.atualizarVeiculo(1L, "", null, null);

        assertEquals("Volvo FH", atualizado.getModelo());
        assertEquals(2020, atualizado.getAnoFabricacao());
        assertEquals(1000L, atualizado.getQuilometragem());
    }

    @Test
    void atualizarVeiculoDeveManterModeloQuandoNulo() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 1000L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        mockSaveRetornaArgumento();

        assertEquals("Volvo FH", veiculoService.atualizarVeiculo(1L, null, null, null).getModelo());
    }

    @Test
    void atualizarVeiculoDevePermitirReduzirQuilometragemApenasComAviso() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 1000L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));
        mockSaveRetornaArgumento();

        assertEquals(500L, veiculoService.atualizarVeiculo(1L, null, null, 500L).getQuilometragem());
    }

    @Test
    void atualizarVeiculoDeveLancarExcecaoQuandoNaoExiste() {
        when(veiculoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> veiculoService.atualizarVeiculo(9L, "X", 2020, 1L));
        verify(veiculoRepository, never()).save(any());
    }

    // obterVeiculosPorStatus

    @Test
    void obterVeiculosPorStatusDeveRetornarListaParaStatusValidos() {
        List<Veiculo> lista = List.of(criarVeiculo(1L, PLACA, 0L));
        for (String status : new String[] {"ATIVO", "INATIVO", "MANUTENCAO"}) {
            when(veiculoRepository.findByStatus(status)).thenReturn(lista);
            assertSame(lista, veiculoService.obterVeiculosPorStatus(status));
        }
    }

    @Test
    void obterVeiculosPorStatusDeveLancarExcecaoQuandoStatusInvalido() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> veiculoService.obterVeiculosPorStatus("OUTRO"));
        assertEquals("Status inválido: OUTRO", ex.getMessage());
        verifyNoInteractions(veiculoRepository);
    }

    // agendarManutencaoPreventiva

    @Test
    void agendarManutencaoPreventivaDeveNotificar() {
        Veiculo veiculo = criarVeiculo(1L, PLACA, 0L);
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        veiculoService.agendarManutencaoPreventiva(1L, 60000L);

        verify(notificador).notificarManutencaoAgendada(veiculo, 60000L);
    }

    @Test
    void sincronizarComSistemaExternoNaoDeveLancarExcecaoNemInteragirComDependencias() {
        veiculoService.sincronizarComSistemaExterno();

        verifyNoInteractions(veiculoRepository, notificador);
    }
}
