package com.rotalog.service;

import com.rotalog.repository.VeiculoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstatisticasFrotaServiceTest {

    @Mock
    private VeiculoRepository veiculoRepository;

    @InjectMocks
    private EstatisticasFrotaService service;

    @Test
    void obterEstatisticasDeveRetornarJsonComContagens() {
        when(veiculoRepository.count()).thenReturn(6L);
        when(veiculoRepository.countByStatus("ATIVO")).thenReturn(3L);
        when(veiculoRepository.countByStatus("INATIVO")).thenReturn(2L);
        when(veiculoRepository.countByStatus("MANUTENCAO")).thenReturn(1L);

        assertEquals("{\"total\": 6, \"ativos\": 3, \"inativos\": 2, \"em_manutencao\": 1}",
                service.obterEstatisticas());
    }

    @Test
    void obterEstatisticasDeveRetornarZerosQuandoFrotaVazia() {
        assertEquals("{\"total\": 0, \"ativos\": 0, \"inativos\": 0, \"em_manutencao\": 0}",
                service.obterEstatisticas());
    }
}
