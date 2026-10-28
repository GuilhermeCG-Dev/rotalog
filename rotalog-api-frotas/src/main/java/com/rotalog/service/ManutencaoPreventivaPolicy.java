package com.rotalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Política de manutenção preventiva: limite de quilometragem e custo estimado.
 */
@Component
public class ManutencaoPreventivaPolicy {

    private final long limiteQuilometragem;
    private final double custoBase;
    private final double custoPorKm;

    public ManutencaoPreventivaPolicy(
            @Value("${rotalog.manutencao.limite-quilometragem:50000}") long limiteQuilometragem,
            @Value("${rotalog.manutencao.custo-base:500.0}") double custoBase,
            @Value("${rotalog.manutencao.custo-por-km:0.05}") double custoPorKm) {
        this.limiteQuilometragem = limiteQuilometragem;
        this.custoBase = custoBase;
        this.custoPorKm = custoPorKm;
    }

    public boolean precisaDeManutencao(Long quilometragem) {
        return quilometragem != null && quilometragem >= limiteQuilometragem;
    }

    public Double calcularCusto(Long quilometragem) {
        return custoBase + (quilometragem * custoPorKm);
    }
}
