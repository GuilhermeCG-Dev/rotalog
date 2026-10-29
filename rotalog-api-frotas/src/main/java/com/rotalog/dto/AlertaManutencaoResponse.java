package com.rotalog.dto;

import com.rotalog.domain.AlertaManutencao;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AlertaManutencaoResponse {

    private final Long id;
    private final Long veiculoId;
    private final String placa;
    private final String motivo;
    private final Long quilometragem;
    private final LocalDateTime dataUltimaManutencao;
    private final String status;
    private final Long notificacaoId;
    private final String erroMensagem;
    private final LocalDateTime dataCriacao;
    private final LocalDateTime dataAtualizacao;

    private AlertaManutencaoResponse(AlertaManutencao alerta) {
        this.id = alerta.getId();
        this.veiculoId = alerta.getVeiculoId();
        this.placa = alerta.getPlaca();
        this.motivo = alerta.getMotivo().name();
        this.quilometragem = alerta.getQuilometragem();
        this.dataUltimaManutencao = alerta.getDataUltimaManutencao();
        this.status = alerta.getStatus().name();
        this.notificacaoId = alerta.getNotificacaoId();
        this.erroMensagem = alerta.getErroMensagem();
        this.dataCriacao = alerta.getDataCriacao();
        this.dataAtualizacao = alerta.getDataAtualizacao();
    }

    public static AlertaManutencaoResponse de(AlertaManutencao alerta) {
        return new AlertaManutencaoResponse(alerta);
    }
}
