package com.rotalog.service;

import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.dto.AlertaManutencaoNotificacaoRequest;
import com.rotalog.dto.NotificacaoStatusResponse;
import com.rotalog.exception.StatusAlertaInvalidoException;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detecta veículos elegíveis à manutenção preventiva, pede o envio do alerta à api-notificacoes
 * (HTTP síncrono) e registra o alerta com o status da notificação.
 *
 * O alerta é gravado como PENDENTE antes da chamada HTTP; se a api-notificacoes estiver fora do ar
 * ele permanece PENDENTE e é reenviado na próxima verificação.
 */
@Slf4j
@Service
public class AlertaManutencaoService {

    private static final String STATUS_VEICULO_ATIVO = "ATIVO";
    private static final String STATUS_MANUTENCAO_CONCLUIDA = "CONCLUIDA";
    private static final List<String> TIPOS_QUE_ZERAM_PRAZO = List.of("PREVENTIVA", "REVISAO");
    private static final Set<StatusAlerta> STATUS_EM_ABERTO = EnumSet.of(StatusAlerta.PENDENTE, StatusAlerta.ENVIADO);
    private static final String SERVICO_ORIGEM = "api-frotas";

    private final VeiculoRepository veiculoRepository;
    private final ManutencaoRepository manutencaoRepository;
    private final AlertaManutencaoRepository alertaRepository;
    private final ManutencaoPreventivaPolicy policy;
    private final NotificacaoClient notificacaoClient;
    private final String destinatarioGestor;

    public AlertaManutencaoService(VeiculoRepository veiculoRepository,
            ManutencaoRepository manutencaoRepository,
            AlertaManutencaoRepository alertaRepository,
            ManutencaoPreventivaPolicy policy,
            NotificacaoClient notificacaoClient,
            @Value("${rotalog.notificacao.destinatario-gestor:gestor@rotalog.com}") String destinatarioGestor) {
        this.veiculoRepository = veiculoRepository;
        this.manutencaoRepository = manutencaoRepository;
        this.alertaRepository = alertaRepository;
        this.policy = policy;
        this.notificacaoClient = notificacaoClient;
        this.destinatarioGestor = destinatarioGestor;
    }

    public List<AlertaManutencao> listar(String status) {
        if (status == null || status.isBlank()) {
            return alertaRepository.findAllByOrderByDataCriacaoDesc();
        }
        return alertaRepository.findByStatusOrderByDataCriacaoDesc(converterStatus(status));
    }

    /**
     * Reenvia alertas PENDENTE, cria alertas para veículos elegíveis sem alerta em aberto
     * e devolve os alertas processados nesta execução.
     */
    public List<AlertaManutencao> verificarEEnviar() {
        LocalDateTime agora = LocalDateTime.now();

        List<AlertaManutencao> aEnviar = new ArrayList<>(alertaRepository.findByStatus(StatusAlerta.PENDENTE));
        Set<Long> veiculosComAlerta = new HashSet<>();
        aEnviar.forEach(alerta -> veiculosComAlerta.add(alerta.getVeiculoId()));

        for (Veiculo veiculo : veiculoRepository.findByStatus(STATUS_VEICULO_ATIVO)) {
            if (veiculosComAlerta.contains(veiculo.getId())
                    || alertaRepository.existsByVeiculoIdAndStatusIn(veiculo.getId(), STATUS_EM_ABERTO)) {
                continue;
            }
            avaliar(veiculo, agora).ifPresent(alerta -> aEnviar.add(alertaRepository.save(alerta)));
        }

        boolean notificacoesDisponivel = true;
        for (AlertaManutencao alerta : aEnviar) {
            if (notificacoesDisponivel) {
                notificacoesDisponivel = enviar(alerta);
            }
        }

        log.info("Verificação de manutenção preventiva: {} alerta(s) processado(s)", aEnviar.size());
        return aEnviar;
    }

    private Optional<AlertaManutencao> avaliar(Veiculo veiculo, LocalDateTime agora) {
        LocalDateTime ultimaManutencao = manutencaoRepository
                .findFirstByVeiculoIdAndStatusAndTipoManutencaoInOrderByDataManutencaoDesc(
                        veiculo.getId(), STATUS_MANUTENCAO_CONCLUIDA, TIPOS_QUE_ZERAM_PRAZO)
                .map(Manutencao::getDataManutencao)
                .orElse(null);
        LocalDateTime referencia = ultimaManutencao != null ? ultimaManutencao : veiculo.getDataCadastro();

        boolean porQuilometragem = policy.precisaDeManutencao(veiculo.getQuilometragem());
        boolean porTempo = policy.manutencaoVencidaPorTempo(referencia, agora);
        if (!porQuilometragem && !porTempo) {
            return Optional.empty();
        }

        AlertaManutencao alerta = new AlertaManutencao();
        alerta.setVeiculoId(veiculo.getId());
        alerta.setPlaca(veiculo.getPlaca());
        alerta.setQuilometragem(veiculo.getQuilometragem());
        alerta.setDataUltimaManutencao(ultimaManutencao);
        alerta.setMotivo(porQuilometragem && porTempo ? MotivoAlerta.QUILOMETRAGEM_E_TEMPO
                : porQuilometragem ? MotivoAlerta.QUILOMETRAGEM : MotivoAlerta.TEMPO);
        alerta.setStatus(StatusAlerta.PENDENTE);
        return Optional.of(alerta);
    }

    /** @return false quando a api-notificacoes está fora do ar (alerta segue PENDENTE). */
    private boolean enviar(AlertaManutencao alerta) {
        try {
            NotificacaoStatusResponse resposta = notificacaoClient.enviarAlertaManutencao(montarRequest(alerta));
            aplicarResposta(alerta, resposta);
        } catch (ResourceAccessException e) {
            log.warn("api-notificacoes indisponível; alerta {} permanece PENDENTE: {}", alerta.getId(), e.getMessage());
            alerta.setStatus(StatusAlerta.PENDENTE);
            alerta.setErroMensagem(e.getMessage());
            alertaRepository.save(alerta);
            return false;
        } catch (RestClientException e) {
            log.error("Falha ao enviar alerta {}: {}", alerta.getId(), e.getMessage());
            alerta.setStatus(StatusAlerta.FALHA);
            alerta.setErroMensagem(e.getMessage());
            alertaRepository.save(alerta);
        }
        return true;
    }

    private void aplicarResposta(AlertaManutencao alerta, NotificacaoStatusResponse resposta) {
        if (resposta == null || resposta.getStatus() == null) {
            alerta.setStatus(StatusAlerta.FALHA);
            alerta.setErroMensagem("Resposta vazia da api-notificacoes");
        } else {
            alerta.setNotificacaoId(resposta.getId());
            alerta.setErroMensagem(resposta.getErroMensagem());
            switch (resposta.getStatus().toUpperCase()) {
                case "ENVIADO":
                    alerta.setStatus(StatusAlerta.ENVIADO);
                    alerta.setErroMensagem(null);
                    break;
                case "FALHA":
                    alerta.setStatus(StatusAlerta.FALHA);
                    break;
                default:
                    alerta.setStatus(StatusAlerta.PENDENTE);
            }
        }
        alertaRepository.save(alerta);
    }

    private AlertaManutencaoNotificacaoRequest montarRequest(AlertaManutencao alerta) {
        String modelo = veiculoRepository.findById(alerta.getVeiculoId()).map(Veiculo::getModelo).orElse(null);
        return AlertaManutencaoNotificacaoRequest.builder()
                .veiculoId(alerta.getVeiculoId())
                .placa(alerta.getPlaca())
                .modelo(modelo)
                .motivo(alerta.getMotivo().name())
                .quilometragem(alerta.getQuilometragem())
                .dataUltimaManutencao(alerta.getDataUltimaManutencao() != null
                        ? alerta.getDataUltimaManutencao().toString() : null)
                .destinatario(destinatarioGestor)
                .servicoOrigem(SERVICO_ORIGEM)
                .referenciaId(alerta.getId())
                .build();
    }

    private StatusAlerta converterStatus(String status) {
        try {
            return StatusAlerta.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new StatusAlertaInvalidoException(status);
        }
    }
}
