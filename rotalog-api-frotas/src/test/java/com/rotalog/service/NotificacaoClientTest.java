package com.rotalog.service;

import com.rotalog.dto.AlertaManutencaoNotificacaoRequest;
import com.rotalog.dto.NotificacaoStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificacaoClientTest {

    private static final String URL = "http://notificacoes.test/api/notificacoes/alerta-manutencao";

    private MockRestServiceServer server;
    private NotificacaoClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new NotificacaoClient(restTemplate, "http://notificacoes.test");
    }

    private AlertaManutencaoNotificacaoRequest request() {
        return AlertaManutencaoNotificacaoRequest.builder()
                .veiculoId(1L).placa("KMA1B23").motivo("QUILOMETRAGEM").quilometragem(85000L)
                .destinatario("gestor@teste.com").servicoOrigem("api-frotas").referenciaId(10L)
                .build();
    }

    @Test
    void deveEnviarPedidoDeAlertaELerStatusDaResposta() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.placa").value("KMA1B23"))
                .andExpect(jsonPath("$.destinatario").value("gestor@teste.com"))
                .andExpect(jsonPath("$.referenciaId").value(10))
                .andRespond(withSuccess("{\"id\":5,\"status\":\"ENVIADO\"}", MediaType.APPLICATION_JSON));

        NotificacaoStatusResponse resposta = client.enviarAlertaManutencao(request());

        assertEquals(5L, resposta.getId());
        assertEquals("ENVIADO", resposta.getStatus());
        server.verify();
    }

    @Test
    void devePropagarErroHttpDaApiDeNotificacoes() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.class, () -> client.enviarAlertaManutencao(request()));
    }
}
