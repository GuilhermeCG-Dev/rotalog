package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VeiculoQuilometragemServiceTest {

    @Mock
    private VeiculoService veiculoService;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private VeiculoNotificador notificador;

    private VeiculoQuilometragemService service;

    @BeforeEach
    void setUp() {
        service = new VeiculoQuilometragemService(veiculoService, veiculoRepository,
                new ManutencaoPreventivaPolicy(50000L, 500.0, 0.05), notificador);
    }

    private Veiculo criarVeiculo(Long quilometragem) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setQuilometragem(quilometragem);
        return veiculo;
    }

    private void mockVeiculo(Veiculo veiculo) {
        when(veiculoService.buscarPorId(1L)).thenReturn(veiculo);
    }

    private void mockSave() {
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void atualizarQuilometragemDeveLancarExcecaoQuandoNegativa() {
        mockVeiculo(criarVeiculo(100L));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.atualizarQuilometragem(1L, -1L));
        assertEquals("Quilometragem não pode ser negativa", ex.getMessage());
        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemNaoDeveNotificarAbaixoDoLimite() {
        mockVeiculo(criarVeiculo(100L));
        mockSave();

        assertEquals(200L, service.atualizarQuilometragem(1L, 200L).getQuilometragem());
        verifyNoInteractions(notificador);
    }

    @Test
    void atualizarQuilometragemDeveAceitarValorMenorQueAtual() {
        mockVeiculo(criarVeiculo(500L));
        mockSave();

        assertEquals(100L, service.atualizarQuilometragem(1L, 100L).getQuilometragem());
    }

    @Test
    void atualizarQuilometragemDeveNotificarQuandoAtingeLimiteManutencao() {
        Veiculo veiculo = criarVeiculo(100L);
        mockVeiculo(veiculo);
        mockSave();

        service.atualizarQuilometragem(1L, 50000L);

        verify(notificador).notificarAlertaManutencao(veiculo, 50000L);
    }

    @Test
    void atualizarQuilometragemDevePropagarExcecaoQuandoVeiculoNaoExiste() {
        when(veiculoService.buscarPorId(9L)).thenThrow(new RuntimeException("Veículo não encontrado: 9"));

        assertThrows(RuntimeException.class, () -> service.atualizarQuilometragem(9L, 10L));
        verifyNoInteractions(veiculoRepository, notificador);
    }
}
