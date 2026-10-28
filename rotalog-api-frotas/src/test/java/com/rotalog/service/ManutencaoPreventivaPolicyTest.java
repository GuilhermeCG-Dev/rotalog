package com.rotalog.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManutencaoPreventivaPolicyTest {

    private final ManutencaoPreventivaPolicy policy = new ManutencaoPreventivaPolicy(50000L, 500.0, 0.05);

    @Test
    void precisaDeManutencaoDeveRetornarTrueNoLimiteOuAcima() {
        assertTrue(policy.precisaDeManutencao(50000L));
        assertTrue(policy.precisaDeManutencao(70000L));
    }

    @Test
    void precisaDeManutencaoDeveRetornarFalseAbaixoDoLimite() {
        assertFalse(policy.precisaDeManutencao(49999L));
    }

    @Test
    void precisaDeManutencaoDeveRetornarFalseQuandoQuilometragemNula() {
        assertFalse(policy.precisaDeManutencao(null));
    }

    @Test
    void precisaDeManutencaoDeveRespeitarLimiteConfigurado() {
        ManutencaoPreventivaPolicy custom = new ManutencaoPreventivaPolicy(1000L, 0, 0);
        assertTrue(custom.precisaDeManutencao(1000L));
        assertFalse(custom.precisaDeManutencao(999L));
    }

    @Test
    void calcularCustoDeveSomarBaseEValorPorKm() {
        assertEquals(1000.0, policy.calcularCusto(10000L), 0.001);
    }

    @Test
    void calcularCustoDeveRetornarSomenteBaseComKmZero() {
        assertEquals(500.0, policy.calcularCusto(0L), 0.001);
    }
}
