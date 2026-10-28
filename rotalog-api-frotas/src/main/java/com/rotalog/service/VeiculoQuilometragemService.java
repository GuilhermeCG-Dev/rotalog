package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import com.rotalog.service.validation.VeiculoValidator;
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
    private final VeiculoValidator validator;
    private final ManutencaoPreventivaPolicy manutencaoPolicy;
    private final VeiculoNotificador notificador;

    /** O odômetro não regride; o alerta de manutenção dispara só ao cruzar o limite. */
    @Transactional
    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
        Veiculo veiculo = veiculoService.buscarPorId(veiculoId);

        Long atual = veiculo.getQuilometragem();
        validator.validarQuilometragem(atual, novaQuilometragem);

        veiculo.setQuilometragem(novaQuilometragem);
        Veiculo atualizado = veiculoRepository.save(veiculo);

        if (!manutencaoPolicy.precisaDeManutencao(atual)
                && manutencaoPolicy.precisaDeManutencao(novaQuilometragem)) {
            notificador.notificarAlertaManutencao(veiculo, novaQuilometragem);
        }

        return atualizado;
    }
}
