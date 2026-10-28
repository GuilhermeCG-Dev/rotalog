package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.exception.TransicaoStatusInvalidaException;
import com.rotalog.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VeiculoStatusService {

    private static final String ATIVO = "ATIVO";
    private static final String INATIVO = "INATIVO";
    private static final String MANUTENCAO = "MANUTENCAO";

    private final VeiculoService veiculoService;
    private final VeiculoRepository veiculoRepository;
    private final VeiculoNotificador notificador;

    /** Idempotente: veículo já inativo é devolvido sem regravar nem renotificar. */
    @Transactional
    public Veiculo desativarVeiculo(Long veiculoId) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);
        if (INATIVO.equals(veiculo.getStatus())) {
            return veiculo;
        }
        if (MANUTENCAO.equals(veiculo.getStatus())) {
            throw new TransicaoStatusInvalidaException(MANUTENCAO, INATIVO);
        }
        veiculo.setStatus(INATIVO);

        Veiculo desativado = veiculoRepository.save(veiculo);
        log.info("Veículo desativado: {}", veiculo.getPlaca());

        notificador.notificarVeiculoDesativado(veiculo);
        return desativado;
    }

    /** Veículo em MANUTENCAO só volta a ATIVO pela conclusão/cancelamento em ManutencaoService. */
    @Transactional
    public Veiculo reativarVeiculo(Long veiculoId) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);
        if (ATIVO.equals(veiculo.getStatus())) {
            return veiculo;
        }
        if (MANUTENCAO.equals(veiculo.getStatus())) {
            throw new TransicaoStatusInvalidaException(MANUTENCAO, ATIVO);
        }
        veiculo.setStatus(ATIVO);

        log.info("Veículo reativado: {}", veiculo.getPlaca());
        return veiculoRepository.save(veiculo);
    }
}
