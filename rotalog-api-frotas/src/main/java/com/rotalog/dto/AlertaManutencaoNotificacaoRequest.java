package com.rotalog.dto;

import lombok.Builder;
import lombok.Getter;

/** Corpo do pedido de alerta enviado à api-notificacoes. */
@Getter
@Builder
public class AlertaManutencaoNotificacaoRequest {

    private final Long veiculoId;
    private final String placa;
    private final String modelo;
    private final String motivo;
    private final Long quilometragem;
    private final String dataUltimaManutencao;
    private final String destinatario;
    private final String servicoOrigem;
    private final Long referenciaId;
}
