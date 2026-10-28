package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VeiculoNotificadorTest {

    private static final String GESTOR = "gestor@teste.com";

    @Mock
    private NotificacaoClient notificacaoClient;

    private VeiculoNotificador notificador;
    private Veiculo veiculo;

    @BeforeEach
    void setUp() {
        notificador = new VeiculoNotificador(notificacaoClient, GESTOR);
        veiculo = new Veiculo();
        veiculo.setPlaca("ABC1234");
        veiculo.setModelo("Volvo FH");
    }

    @Test
    void notificarNovoVeiculoDeveEnviarTipoDestinatarioEMensagem() {
        notificador.notificarNovoVeiculo(veiculo);

        verify(notificacaoClient).enviarNotificacao("NOVO_VEICULO", GESTOR,
                "Novo veículo cadastrado: ABC1234 - Volvo FH");
    }

    @Test
    void notificarAlertaManutencaoDeveEnviarTipoDestinatarioEMensagem() {
        notificador.notificarAlertaManutencao(veiculo, 50000L);

        verify(notificacaoClient).enviarNotificacao("ALERTA_MANUTENCAO", GESTOR,
                "Veículo ABC1234 atingiu 50000 km. Agendar manutenção preventiva.");
    }

    @Test
    void notificarManutencaoAgendadaDeveEnviarTipoDestinatarioEMensagem() {
        notificador.notificarManutencaoAgendada(veiculo, 60000L);

        verify(notificacaoClient).enviarNotificacao("MANUTENCAO_AGENDADA", GESTOR,
                "Manutenção preventiva agendada para veículo ABC1234 em 60000 km");
    }

    @Test
    void notificarVeiculoDesativadoDeveEnviarTipoDestinatarioEMensagem() {
        notificador.notificarVeiculoDesativado(veiculo);

        verify(notificacaoClient).enviarNotificacao("VEICULO_DESATIVADO", GESTOR,
                "Veículo ABC1234 foi desativado");
    }

    @Test
    void notificarNaoDevePropagarFalhaDoClient() {
        doThrow(new RuntimeException("fora do ar"))
                .when(notificacaoClient).enviarNotificacao(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificador.notificarNovoVeiculo(veiculo));
        assertDoesNotThrow(() -> notificador.notificarAlertaManutencao(veiculo, 1L));
        assertDoesNotThrow(() -> notificador.notificarManutencaoAgendada(veiculo, 1L));
        assertDoesNotThrow(() -> notificador.notificarVeiculoDesativado(veiculo));
    }
}
