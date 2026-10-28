package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Único ponto de integração do domínio de veículos com a api-notificacoes.
 * Falhas de envio são registradas e nunca interrompem o fluxo de negócio.
 */
@Slf4j
@Component
public class VeiculoNotificador {

    static final String NOVO_VEICULO = "NOVO_VEICULO";
    static final String ALERTA_MANUTENCAO = "ALERTA_MANUTENCAO";
    static final String MANUTENCAO_AGENDADA = "MANUTENCAO_AGENDADA";
    static final String VEICULO_DESATIVADO = "VEICULO_DESATIVADO";

    private final NotificacaoClient notificacaoClient;
    private final String destinatarioGestor;

    public VeiculoNotificador(NotificacaoClient notificacaoClient,
            @Value("${rotalog.notificacao.destinatario-gestor:gestor@rotalog.com}") String destinatarioGestor) {
        this.notificacaoClient = notificacaoClient;
        this.destinatarioGestor = destinatarioGestor;
    }

    public void notificarNovoVeiculo(Veiculo veiculo) {
        enviar(NOVO_VEICULO,
                "Novo veículo cadastrado: " + veiculo.getPlaca() + " - " + veiculo.getModelo());
    }

    public void notificarAlertaManutencao(Veiculo veiculo, Long quilometragem) {
        enviar(ALERTA_MANUTENCAO,
                "Veículo " + veiculo.getPlaca() + " atingiu " + quilometragem
                        + " km. Agendar manutenção preventiva.");
    }

    public void notificarManutencaoAgendada(Veiculo veiculo, Long quilometragemLimite) {
        enviar(MANUTENCAO_AGENDADA,
                "Manutenção preventiva agendada para veículo " + veiculo.getPlaca()
                        + " em " + quilometragemLimite + " km");
    }

    public void notificarVeiculoDesativado(Veiculo veiculo) {
        enviar(VEICULO_DESATIVADO, "Veículo " + veiculo.getPlaca() + " foi desativado");
    }

    private void enviar(String tipo, String mensagem) {
        try {
            notificacaoClient.enviarNotificacao(tipo, destinatarioGestor, mensagem);
        } catch (Exception e) {
            log.error("Falha ao enviar notificação {}", tipo, e);
        }
    }
}
