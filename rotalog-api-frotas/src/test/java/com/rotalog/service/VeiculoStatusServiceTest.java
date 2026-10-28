package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class VeiculoStatusServiceTest {

    @Mock
    private VeiculoService veiculoService;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private VeiculoNotificador notificador;

    @InjectMocks
    private VeiculoStatusService service;

    private Veiculo criarVeiculo(String status) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("ABC1234");
        veiculo.setStatus(status);
        return veiculo;
    }

    @Test
    void desativarVeiculoDeveMarcarInativoENotificar() {
        Veiculo veiculo = criarVeiculo("ATIVO");
        when(veiculoService.buscarPorId(1L)).thenReturn(veiculo);
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo desativado = service.desativarVeiculo(1L);

        assertEquals("INATIVO", desativado.getStatus());
        verify(notificador).notificarVeiculoDesativado(veiculo);
    }

    @Test
    void desativarVeiculoDevePropagarExcecaoQuandoNaoExiste() {
        when(veiculoService.buscarPorId(9L)).thenThrow(new RuntimeException("Veículo não encontrado: 9"));

        assertThrows(RuntimeException.class, () -> service.desativarVeiculo(9L));
        verify(veiculoRepository, never()).save(any());
        verifyNoInteractions(notificador);
    }

    @Test
    void reativarVeiculoDeveMarcarAtivoSemNotificar() {
        Veiculo veiculo = criarVeiculo("INATIVO");
        when(veiculoService.buscarPorId(1L)).thenReturn(veiculo);
        when(veiculoRepository.save(any(Veiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        Veiculo reativado = service.reativarVeiculo(1L);

        assertEquals("ATIVO", reativado.getStatus());
        verifyNoInteractions(notificador);
    }

    @Test
    void reativarVeiculoDevePropagarExcecaoQuandoNaoExiste() {
        when(veiculoService.buscarPorId(9L)).thenThrow(new RuntimeException("Veículo não encontrado: 9"));

        assertThrows(RuntimeException.class, () -> service.reativarVeiculo(9L));
        verify(veiculoRepository, never()).save(any());
    }
}
