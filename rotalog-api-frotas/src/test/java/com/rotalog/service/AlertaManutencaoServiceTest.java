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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaManutencaoServiceTest {

    private static final String GESTOR = "gestor@teste.com";

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private ManutencaoRepository manutencaoRepository;

    @Mock
    private AlertaManutencaoRepository alertaRepository;

    @Mock
    private NotificacaoClient notificacaoClient;

    private AlertaManutencaoService service;

    @BeforeEach
    void setUp() {
        service = new AlertaManutencaoService(veiculoRepository, manutencaoRepository, alertaRepository,
                new ManutencaoPreventivaPolicy(50000L, 6, 500.0, 0.05), notificacaoClient, GESTOR);
        // save devolve o próprio alerta, atribuindo id aos novos
        lenient().when(alertaRepository.save(any(AlertaManutencao.class))).thenAnswer(invocation -> {
            AlertaManutencao alerta = invocation.getArgument(0);
            if (alerta.getId() == null) {
                alerta.setId(100L + alerta.getVeiculoId());
            }
            return alerta;
        });
    }

    private Veiculo veiculo(Long id, String placa, long km, LocalDateTime cadastro) {
        Veiculo veiculo = new Veiculo();
        veiculo.setId(id);
        veiculo.setPlaca(placa);
        veiculo.setModelo("Modelo " + placa);
        veiculo.setQuilometragem(km);
        veiculo.setStatus("ATIVO");
        veiculo.setDataCadastro(cadastro);
        return veiculo;
    }

    private void ultimaManutencao(Long veiculoId, LocalDateTime data) {
        Manutencao manutencao = new Manutencao();
        manutencao.setVeiculoId(veiculoId);
        manutencao.setDataManutencao(data);
        when(manutencaoRepository.findFirstByVeiculoIdAndStatusAndTipoManutencaoInOrderByDataManutencaoDesc(
                eq(veiculoId), eq("CONCLUIDA"), anyCollection())).thenReturn(Optional.of(manutencao));
    }

    private void semManutencao(Long veiculoId) {
        when(manutencaoRepository.findFirstByVeiculoIdAndStatusAndTipoManutencaoInOrderByDataManutencaoDesc(
                eq(veiculoId), anyString(), anyCollection())).thenReturn(Optional.empty());
    }

    private void respostaDaNotificacao(String status, Long id, String erro) {
        NotificacaoStatusResponse resposta = new NotificacaoStatusResponse();
        resposta.setId(id);
        resposta.setStatus(status);
        resposta.setErroMensagem(erro);
        when(notificacaoClient.enviarAlertaManutencao(any())).thenReturn(resposta);
    }

    @Test
    void deveGerarAlertaPorQuilometragemEMarcarEnviado() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(v));
        ultimaManutencao(1L, LocalDateTime.now().minusDays(20));
        respostaDaNotificacao("ENVIADO", 7L, null);

        List<AlertaManutencao> alertas = service.verificarEEnviar();

        assertEquals(1, alertas.size());
        AlertaManutencao alerta = alertas.get(0);
        assertEquals(MotivoAlerta.QUILOMETRAGEM, alerta.getMotivo());
        assertEquals(StatusAlerta.ENVIADO, alerta.getStatus());
        assertEquals(7L, alerta.getNotificacaoId());
        assertEquals(85000L, alerta.getQuilometragem());

        ArgumentCaptor<AlertaManutencaoNotificacaoRequest> request =
                ArgumentCaptor.forClass(AlertaManutencaoNotificacaoRequest.class);
        verify(notificacaoClient).enviarAlertaManutencao(request.capture());
        assertEquals("KMA1B23", request.getValue().getPlaca());
        assertEquals("Modelo KMA1B23", request.getValue().getModelo());
        assertEquals("QUILOMETRAGEM", request.getValue().getMotivo());
        assertEquals(GESTOR, request.getValue().getDestinatario());
        assertEquals("api-frotas", request.getValue().getServicoOrigem());
        assertEquals(101L, request.getValue().getReferenciaId());
    }

    @Test
    void deveGerarAlertaPorTempoQuandoUltimaManutencaoPassouDoLimite() {
        Veiculo v = veiculo(2L, "TMP2C34", 20000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(2L)).thenReturn(Optional.of(v));
        ultimaManutencao(2L, LocalDateTime.now().minusMonths(8));
        respostaDaNotificacao("ENVIADO", 8L, null);

        List<AlertaManutencao> alertas = service.verificarEEnviar();

        assertEquals(MotivoAlerta.TEMPO, alertas.get(0).getMotivo());
    }

    @Test
    void deveGerarAlertaPorQuilometragemETempo() {
        Veiculo v = veiculo(3L, "AMB3D45", 130000, LocalDateTime.now().minusYears(3));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(3L)).thenReturn(Optional.of(v));
        ultimaManutencao(3L, LocalDateTime.now().minusMonths(14));
        respostaDaNotificacao("ENVIADO", 9L, null);

        assertEquals(MotivoAlerta.QUILOMETRAGEM_E_TEMPO, service.verificarEEnviar().get(0).getMotivo());
    }

    @Test
    void semManutencaoDeveUsarDataDeCadastroComoReferencia() {
        Veiculo v = veiculo(4L, "SEM4E56", 10000, LocalDateTime.now().minusMonths(10));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(4L)).thenReturn(Optional.of(v));
        semManutencao(4L);
        respostaDaNotificacao("ENVIADO", 10L, null);

        AlertaManutencao alerta = service.verificarEEnviar().get(0);

        assertEquals(MotivoAlerta.TEMPO, alerta.getMotivo());
        assertNull(alerta.getDataUltimaManutencao());
    }

    @Test
    void naoDeveGerarAlertaParaVeiculoEmDia() {
        Veiculo v = veiculo(5L, "OKK5F67", 12000, LocalDateTime.now().minusYears(1));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        ultimaManutencao(5L, LocalDateTime.now().minusMonths(1));

        assertTrue(service.verificarEEnviar().isEmpty());
        verify(notificacaoClient, never()).enviarAlertaManutencao(any());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void naoDeveDuplicarAlertaQuandoJaExisteEmAberto() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(alertaRepository.existsByVeiculoIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);

        assertTrue(service.verificarEEnviar().isEmpty());
        verify(notificacaoClient, never()).enviarAlertaManutencao(any());
    }

    @Test
    void deveMarcarFalhaQuandoNotificacoesRetornaFalha() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(v));
        ultimaManutencao(1L, LocalDateTime.now().minusDays(20));
        respostaDaNotificacao("FALHA", 11L, "SMTP connection timeout");

        AlertaManutencao alerta = service.verificarEEnviar().get(0);

        assertEquals(StatusAlerta.FALHA, alerta.getStatus());
        assertEquals("SMTP connection timeout", alerta.getErroMensagem());
    }

    @Test
    void deveMarcarFalhaQuandoNotificacoesRespondeErroHttp() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(v));
        ultimaManutencao(1L, LocalDateTime.now().minusDays(20));
        when(notificacaoClient.enviarAlertaManutencao(any())).thenThrow(new RestClientException("500"));

        assertEquals(StatusAlerta.FALHA, service.verificarEEnviar().get(0).getStatus());
    }

    @Test
    void deveManterPendenteQuandoNotificacoesEstaForaDoAr() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(v));
        ultimaManutencao(1L, LocalDateTime.now().minusDays(20));
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        AlertaManutencao alerta = service.verificarEEnviar().get(0);

        assertEquals(StatusAlerta.PENDENTE, alerta.getStatus());
        assertEquals("Connection refused", alerta.getErroMensagem());
    }

    @Test
    void naoDeveInsistirNasDemaisChamadasQuandoNotificacoesEstaForaDoAr() {
        Veiculo a = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        Veiculo b = veiculo(2L, "AMB3D45", 130000, LocalDateTime.now().minusYears(3));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(a, b));
        when(veiculoRepository.findById(anyLong())).thenReturn(Optional.of(a));
        ultimaManutencao(1L, LocalDateTime.now().minusDays(20));
        ultimaManutencao(2L, LocalDateTime.now().minusDays(20));
        when(notificacaoClient.enviarAlertaManutencao(any()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        List<AlertaManutencao> alertas = service.verificarEEnviar();

        assertEquals(2, alertas.size());
        assertTrue(alertas.stream().allMatch(alerta -> alerta.getStatus() == StatusAlerta.PENDENTE));
        verify(notificacaoClient, times(1)).enviarAlertaManutencao(any());
    }

    @Test
    void deveReenviarAlertaPendenteSemCriarOutroParaOMesmoVeiculo() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        AlertaManutencao pendente = new AlertaManutencao();
        pendente.setId(50L);
        pendente.setVeiculoId(1L);
        pendente.setPlaca("KMA1B23");
        pendente.setMotivo(MotivoAlerta.QUILOMETRAGEM);
        pendente.setStatus(StatusAlerta.PENDENTE);
        when(alertaRepository.findByStatus(StatusAlerta.PENDENTE)).thenReturn(List.of(pendente));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(v));
        respostaDaNotificacao("ENVIADO", 12L, null);

        List<AlertaManutencao> alertas = service.verificarEEnviar();

        assertEquals(1, alertas.size());
        assertEquals(50L, alertas.get(0).getId());
        assertEquals(StatusAlerta.ENVIADO, alertas.get(0).getStatus());
        verify(notificacaoClient, times(1)).enviarAlertaManutencao(any());
    }

    @Test
    void respostaVaziaDeveMarcarFalha() {
        Veiculo v = veiculo(1L, "KMA1B23", 85000, LocalDateTime.now().minusYears(2));
        when(veiculoRepository.findByStatus("ATIVO")).thenReturn(List.of(v));
        when(veiculoRepository.findById(1L)).thenReturn(Optional.of(v));
        ultimaManutencao(1L, LocalDateTime.now().minusDays(20));
        when(notificacaoClient.enviarAlertaManutencao(any())).thenReturn(null);

        assertEquals(StatusAlerta.FALHA, service.verificarEEnviar().get(0).getStatus());
    }

    @Test
    void listarSemFiltroDeveRetornarTodos() {
        service.listar(null);

        verify(alertaRepository).findAllByOrderByDataCriacaoDesc();
    }

    @Test
    void listarDeveFiltrarPorStatusIgnorandoCaixa() {
        service.listar("pendente");

        verify(alertaRepository).findByStatusOrderByDataCriacaoDesc(StatusAlerta.PENDENTE);
    }

    @Test
    void listarComStatusInvalidoDeveLancarExcecao() {
        assertThrows(StatusAlertaInvalidoException.class, () -> service.listar("XPTO"));
    }
}
