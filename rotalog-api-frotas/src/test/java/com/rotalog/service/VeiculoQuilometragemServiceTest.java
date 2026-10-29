package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.exception.QuilometragemInvalidaException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.VeiculoRepository;
import com.rotalog.service.validation.VeiculoValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        service = new VeiculoQuilometragemService(veiculoService, veiculoRepository, new VeiculoValidator(),
                new ManutencaoPreventivaPolicy(50000L, 6, 500.0, 0.05), notificador);
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

        RuntimeException ex = assertThrows(QuilometragemInvalidaException.class,
                () -> service.atualizarQuilometragem(1L, -1L));
        assertEquals("Quilometragem não pode ser negativa", ex.getMessage());
        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemDeveLancarExcecaoQuandoNula() {
        mockVeiculo(criarVeiculo(100L));

        assertThrows(QuilometragemInvalidaException.class, () -> service.atualizarQuilometragem(1L, null));
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
    void atualizarQuilometragemDeveRejeitarValorMenorQueAtual() {
        Veiculo veiculo = criarVeiculo(500L);
        mockVeiculo(veiculo);

        RuntimeException ex = assertThrows(QuilometragemInvalidaException.class,
                () -> service.atualizarQuilometragem(1L, 100L));
        assertEquals("Quilometragem não pode ser menor que a atual", ex.getMessage());
        assertEquals(500L, veiculo.getQuilometragem());
        verify(veiculoRepository, never()).save(any());
    }

    @Test
    void atualizarQuilometragemDeveAceitarValorIgualAoAtual() {
        mockVeiculo(criarVeiculo(500L));
        mockSave();

        assertEquals(500L, service.atualizarQuilometragem(1L, 500L).getQuilometragem());
    }

    @Test
    void atualizarQuilometragemDeveAceitarQuandoAtualNula() {
        mockVeiculo(criarVeiculo(null));
        mockSave();

        assertEquals(100L, service.atualizarQuilometragem(1L, 100L).getQuilometragem());
    }

    @Test
    void atualizarQuilometragemDeveNotificarQuandoCruzaLimiteManutencao() {
        Veiculo veiculo = criarVeiculo(100L);
        mockVeiculo(veiculo);
        mockSave();

        service.atualizarQuilometragem(1L, 50000L);

        verify(notificador).notificarAlertaManutencao(veiculo, 50000L);
    }

    @Test
    void atualizarQuilometragemNaoDeveRenotificarQuandoJaEstavaAcimaDoLimite() {
        mockVeiculo(criarVeiculo(50000L));
        mockSave();

        service.atualizarQuilometragem(1L, 50100L);

        verifyNoInteractions(notificador);
    }

    @Test
    void atualizarQuilometragemNaoDevePerderAlertaQuandoAtualNulaECruzaLimite() {
        Veiculo veiculo = criarVeiculo(null);
        mockVeiculo(veiculo);
        mockSave();

        service.atualizarQuilometragem(1L, 60000L);

        verify(notificador).notificarAlertaManutencao(veiculo, 60000L);
    }

    @Test
    void atualizarQuilometragemDevePropagarExcecaoQuandoVeiculoNaoExiste() {
        when(veiculoService.buscarPorId(9L)).thenThrow(VeiculoNaoEncontradoException.porId(9L));

        assertThrows(VeiculoNaoEncontradoException.class, () -> service.atualizarQuilometragem(9L, 10L));
        verifyNoInteractions(veiculoRepository, notificador);
    }
}
