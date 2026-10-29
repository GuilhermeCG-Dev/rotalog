package com.rotalog.service;

import com.rotalog.config.RestTemplateConfig;
import com.rotalog.domain.AlertaManutencao;
import com.rotalog.domain.Manutencao;
import com.rotalog.domain.MotivoAlerta;
import com.rotalog.domain.StatusAlerta;
import com.rotalog.domain.Veiculo;
import com.rotalog.repository.AlertaManutencaoRepository;
import com.rotalog.repository.ManutencaoRepository;
import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Integração do fluxo service -> client -> HTTP com o contexto Spring real (propriedades, beans e
 * RestTemplate). Só a api-notificacoes (MockRestServiceServer) e os repositórios são simulados.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AlertaManutencaoService.class, NotificacaoClient.class,
        ManutencaoPreventivaPolicy.class, RestTemplateConfig.class})
@ImportAutoConfiguration(RestTemplateAutoConfiguration.class)
@TestPropertySource(properties = {
        "rotalog.api.notificacoes.url=http://notificacoes.test",
        "rotalog.manutencao.limite-quilometragem=50000",
        "rotalog.manutencao.meses-limite=6",
        "rotalog.notificacao.destinatario-gestor=gestor@integracao.com"
})
class AlertaManutencaoIntegracaoTest {

    private static final String URL = "http://notificacoes.test/api/notificacoes/alerta-manutencao";

    @Autowired
    private AlertaManutencaoService service;

    @Autowired
    private RestTemplate restTemplate;

    @MockBean
    private VeiculoRepository veiculoRepository;

    @MockBean
    private ManutencaoRepository manutencaoRepository;

    @MockBean
    private AlertaManutencaoRepository alertaRepository;

    private MockRestServiceServer notificacoes;

    @BeforeEach
    void setUp() {
        notificacoes = MockRestServiceServer.bindTo(restTemplate).build();

        Veiculo veiculo = new Veiculo();
        veiculo.setId(1L);
        veiculo.setPlaca("KMA1B23");
        veiculo.setModelo("Mercedes Accelo 815");
        veiculo.setQuilometragem(85000L);
        veiculo.setStatus("ATIVO");
        veiculo.setDataCadastro(LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(veiculo));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(veiculo));

        Manutencao ultima = new Manutencao();
        ultima.setDataManutencao(LocalDateTime.now().minusDays(20));
        when(manutencaoRepository.findFirstByVeiculoIdAndStatusAndTipoManutencaoInOrderByDataManutencaoDesc(
                eq(1L), anyString(), anyCollection())).thenReturn(Optional.of(ultima));

        when(alertaRepository.save(any(AlertaManutencao.class))).thenAnswer(invocation -> {
            AlertaManutencao alerta = invocation.getArgument(0);
            if (alerta.getId() == null) {
                alerta.setId(1L);
            }
            return alerta;
        });
    }

    @Test
    void deveEnviarPedidoPelaUrlConfiguradaERegistrarAlertaEnviado() {
        notificacoes.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.placa").value("KMA1B23"))
                .andExpect(jsonPath("$.modelo").value("Mercedes Accelo 815"))
                .andExpect(jsonPath("$.motivo").value("QUILOMETRAGEM"))
                .andExpect(jsonPath("$.destinatario").value("gestor@integracao.com"))
                .andExpect(jsonPath("$.servicoOrigem").value("api-frotas"))
                .andExpect(jsonPath("$.referenciaId").value(1))
                .andRespond(withSuccess("{\"id\":42,\"status\":\"ENVIADO\"}", MediaType.APPLICATION_JSON));

        List<AlertaManutencao> alertas = service.verificarEEnviar();

        assertEquals(1, alertas.size());
        assertEquals(StatusAlerta.ENVIADO, alertas.get(0).getStatus());
        assertEquals(MotivoAlerta.QUILOMETRAGEM, alertas.get(0).getMotivo());
        assertEquals(42L, alertas.get(0).getNotificacaoId());
        notificacoes.verify();
    }

    @Test
    void deveRegistrarFalhaQuandoNotificacoesInformaFalhaNoEnvio() {
        notificacoes.expect(once(), requestTo(URL))
                .andRespond(withSuccess("{\"id\":43,\"status\":\"FALHA\",\"erroMensagem\":\"SMTP timeout\"}",
                        MediaType.APPLICATION_JSON));

        AlertaManutencao alerta = service.verificarEEnviar().get(0);

        assertEquals(StatusAlerta.FALHA, alerta.getStatus());
        assertEquals("SMTP timeout", alerta.getErroMensagem());
    }

    @Test
    void deveRegistrarFalhaQuandoNotificacoesRespondeErro500() {
        notificacoes.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals(StatusAlerta.FALHA, service.verificarEEnviar().get(0).getStatus());
    }

    @Test
    void deveManterAlertaPendenteQuandoNotificacoesEstaForaDoAr() {
        notificacoes.expect(once(), requestTo(URL)).andRespond(withException(new IOException("Connection refused")));

        AlertaManutencao alerta = service.verificarEEnviar().get(0);

        assertEquals(StatusAlerta.PENDENTE, alerta.getStatus());
        assertTrue(alerta.getErroMensagem().contains("Connection refused"));
    }

    @Test
    void deveReenviarAlertaPendenteQuandoNotificacoesVoltar() {
        AlertaManutencao pendente = new AlertaManutencao();
        pendente.setId(1L);
        pendente.setVeiculoId(1L);
        pendente.setPlaca("KMA1B23");
        pendente.setMotivo(MotivoAlerta.QUILOMETRAGEM);
        pendente.setStatus(StatusAlerta.PENDENTE);
        when(alertaRepository.findByStatus(StatusAlerta.PENDENTE)).thenReturn(List.of(pendente));
        notificacoes.expect(once(), requestTo(URL))
                .andRespond(withSuccess("{\"id\":44,\"status\":\"ENVIADO\"}", MediaType.APPLICATION_JSON));

        List<AlertaManutencao> alertas = service.verificarEEnviar();

        assertEquals(1, alertas.size());
        assertEquals(StatusAlerta.ENVIADO, pendente.getStatus());
        assertEquals(44L, pendente.getNotificacaoId());
        notificacoes.verify();
    }
}
