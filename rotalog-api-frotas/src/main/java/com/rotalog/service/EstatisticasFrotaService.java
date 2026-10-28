package com.rotalog.service;

import com.rotalog.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EstatisticasFrotaService {

    private final VeiculoRepository veiculoRepository;

    @Transactional(readOnly = true)
    public String obterEstatisticas() {
        long total = veiculoRepository.count();
        long ativos = veiculoRepository.countByStatus("ATIVO");
        long inativos = veiculoRepository.countByStatus("INATIVO");
        long emManutencao = veiculoRepository.countByStatus("MANUTENCAO");

        return String.format(
                "{\"total\": %d, \"ativos\": %d, \"inativos\": %d, \"em_manutencao\": %d}",
                total, ativos, inativos, emManutencao);
    }
}
