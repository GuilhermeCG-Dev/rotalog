package com.rotalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Alerta de manutenção preventiva e o status do envio da notificação ao gestor.
 */
@Entity
@Table(name = "alertas_manutencao")
@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    @Column(name = "placa")
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false)
    private MotivoAlerta motivo;

    @Column(name = "quilometragem")
    private Long quilometragem;

    @Column(name = "data_ultima_manutencao")
    private LocalDateTime dataUltimaManutencao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusAlerta status;

    @Column(name = "notificacao_id")
    private Long notificacaoId;

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @PrePersist
    void aoCriar() {
        dataCriacao = LocalDateTime.now();
        dataAtualizacao = dataCriacao;
    }

    @PreUpdate
    void aoAtualizar() {
        dataAtualizacao = LocalDateTime.now();
    }
}
