package com.rotalog.service;

import com.rotalog.dto.AlertaManutencaoNotificacaoRequest;
import com.rotalog.dto.NotificacaoStatusResponse;
import com.rotalog.logging.CorrelationId;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * NotificacaoClient - HTTP client for api-notificacoes
 * 
 * FIXME: Sem circuit breaker
 * FIXME: Sem retry logic
 */
@Slf4j
@Component
public class NotificacaoClient {

    private final RestTemplate restTemplate;
    private final String notificacaoApiUrl;

    public NotificacaoClient(RestTemplate restTemplate,
            @Value("${rotalog.api.notificacoes.url:http://localhost:5000}") String notificacaoApiUrl) {
        this.restTemplate = restTemplate;
        this.notificacaoApiUrl = notificacaoApiUrl;
    }

    /**
     * Pede o envio do alerta de manutenção preventiva ao gestor e devolve o status da notificação.
     * Propaga {@link org.springframework.web.client.RestClientException}; um
     * {@link org.springframework.web.client.ResourceAccessException} indica api-notificacoes fora do ar.
     */
    public NotificacaoStatusResponse enviarAlertaManutencao(AlertaManutencaoNotificacaoRequest request) {
        String url = notificacaoApiUrl + "/api/notificacoes/alerta-manutencao";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }

        log.info("Chamando api-notificacoes: url={} veiculoId={}", url, request.getVeiculoId());
        ResponseEntity<NotificacaoStatusResponse> resposta = restTemplate.postForEntity(
                url, new HttpEntity<>(request, headers), NotificacaoStatusResponse.class);
        log.info("Resposta da api-notificacoes: veiculoId={} httpStatus={}",
                request.getVeiculoId(), resposta.getStatusCodeValue());
        return resposta.getBody();
    }

    /**
     * Envia notificação para api-notificacoes
     * 
     * FIXME: Sem retry
     * FIXME: Sem fallback
     * FIXME: Sem validação de resposta
     * FIXME: Engolindo exceções no chamador
     */
    public void enviarNotificacao(String tipo, String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", tipo);
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "email"); // FIXME: canal hardcoded

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String url = notificacaoApiUrl + "/api/notificacoes";
            log.info("Enviando notificação para {}: tipo={}, destinatario={}", url, tipo, destinatario);

            restTemplate.postForEntity(url, request, String.class);

            log.info("Notificação enviada com sucesso: tipo={}", tipo);
        } catch (Exception e) {
            // FIXME: Engolindo exceção - apenas logando
            log.error("Erro ao enviar notificação: tipo={}, erro={}", tipo, e.getMessage());
            // FIXME: Sem retry, sem fallback, sem dead letter queue
            throw e; // re-throw para o chamador decidir o que fazer
        }
    }

    /**
     * Envia notificação SMS
     * 
     * FIXME: Código duplicado com enviarNotificacao
     * FIXME: Deveria ser o mesmo método com canal diferente
     */
    public void enviarSms(String destinatario, String mensagem) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("tipo", "SMS");
            body.put("destinatario", destinatario);
            body.put("mensagem", mensagem);
            body.put("canal", "sms");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            String url = notificacaoApiUrl + "/api/notificacoes";
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Erro ao enviar SMS: {}", e.getMessage());
        }
    }
}
