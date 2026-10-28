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
public class VeiculoQuilometragemService {

    private final VeiculoService veiculoService;
    private final VeiculoRepository veiculoRepository;
    private final ManutencaoPreventivaPolicy manutencaoPolicy;
    private final VeiculoNotificador notificador;

    @Transactional
    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);

        if (novaQuilometragem < 0) {
            throw new RuntimeException("Quilometragem não pode ser negativa");
        }

        if (novaQuilometragem < veiculo.getQuilometragem()) {
            log.warn("Quilometragem do veículo {} menor que a atual: {} -> {}",
                    veiculoId, veiculo.getQuilometragem(), novaQuilometragem);
        }

        veiculo.setQuilometragem(novaQuilometragem);
        Veiculo atualizado = veiculoRepository.save(veiculo);

        if (manutencaoPolicy.precisaDeManutencao(novaQuilometragem)) {
            notificador.notificarAlertaManutencao(veiculo, novaQuilometragem);
        }

        return atualizado;
    }
}
