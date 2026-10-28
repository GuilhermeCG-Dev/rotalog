package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VeiculoStatusService {

    private final VeiculoService veiculoService;
    private final VeiculoRepository veiculoRepository;
    private final VeiculoNotificador notificador;

    @Transactional
    public Veiculo desativarVeiculo(Long veiculoId) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);
        veiculo.setStatus("INATIVO");

        Veiculo desativado = veiculoRepository.save(veiculo);
        log.info("Veículo desativado: {}", veiculo.getPlaca());

        notificador.notificarVeiculoDesativado(veiculo);
        return desativado;
    }

    @Transactional
    public Veiculo reativarVeiculo(Long veiculoId) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);
        veiculo.setStatus("ATIVO");

        log.info("Veículo reativado: {}", veiculo.getPlaca());
        return veiculoRepository.save(veiculo);
    }
}
