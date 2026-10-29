package com.rotalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Política de manutenção preventiva: limites de quilometragem e de tempo, e custo estimado.
 */
@Component
public class ManutencaoPreventivaPolicy {

    private final long limiteQuilometragem;
    private final int mesesLimite;
    private final double custoBase;
    private final double custoPorKm;

    public ManutencaoPreventivaPolicy(
            @Value("${rotalog.manutencao.limite-quilometragem:50000}") long limiteQuilometragem,
            @Value("${rotalog.manutencao.meses-limite:6}") int mesesLimite,
            @Value("${rotalog.manutencao.custo-base:500.0}") double custoBase,
            @Value("${rotalog.manutencao.custo-por-km:0.05}") double custoPorKm) {
        this.limiteQuilometragem = limiteQuilometragem;
        this.mesesLimite = mesesLimite;
        this.custoBase = custoBase;
        this.custoPorKm = custoPorKm;
    }

    public boolean precisaDeManutencao(Long quilometragem) {
        return quilometragem != null && quilometragem >= limiteQuilometragem;
    }

    /** Vencida quando a referência (última manutenção) é anterior a {@code agora - mesesLimite}. */
    public boolean manutencaoVencidaPorTempo(LocalDateTime referencia, LocalDateTime agora) {
        return referencia != null && referencia.plusMonths(mesesLimite).isBefore(agora);
    }

    public Double calcularCusto(Long quilometragem) {
        return custoBase + (quilometragem * custoPorKm);
    }
}
