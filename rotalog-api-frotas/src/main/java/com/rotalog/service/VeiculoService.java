package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.exception.PlacaDuplicadaException;
import com.rotalog.exception.StatusVeiculoInvalidoException;
import com.rotalog.exception.VeiculoNaoEncontradoException;
import com.rotalog.repository.VeiculoRepository;
import com.rotalog.service.validation.VeiculoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consulta, cadastro e atualização de veículos. Status, quilometragem, estatísticas,
 * validação, política de manutenção e notificação ficam em classes próprias.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VeiculoService {

    private static final Set<String> STATUS_VALIDOS = Set.of("ATIVO", "INATIVO", "MANUTENCAO");

    private final VeiculoRepository veiculoRepository;
    private final VeiculoValidator validator;
    private final VeiculoNotificador notificador;

    // FIXME: Sem paginação - pode retornar milhares de registros
    @Transactional(readOnly = true)
    public List<Veiculo> listarTodos() {
        return veiculoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Veiculo buscarPorId(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> VeiculoNaoEncontradoException.porId(id));
    }

    @Transactional(readOnly = true)
    public Veiculo buscarPorPlaca(String placa) {
        return veiculoRepository.findByPlaca(validator.normalizarPlaca(placa))
                .orElseThrow(() -> VeiculoNaoEncontradoException.porPlaca(placa));
    }

    @Transactional
    public Veiculo registrarVeiculo(String placa, String modelo, Integer anoFabricacao) {
        String placaNormalizada = validator.validarPlaca(placa);

        if (veiculoRepository.findByPlaca(placaNormalizada).isPresent()) {
            throw new PlacaDuplicadaException(placaNormalizada);
        }

        validator.validarModelo(modelo);
        validator.validarAnoFabricacao(anoFabricacao);

        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placaNormalizada);
        veiculo.setModelo(modelo);
        veiculo.setAnoFabricacao(anoFabricacao);
        veiculo.setStatus("ATIVO");
        veiculo.setQuilometragem(0L);

        Veiculo salvo;
        try {
            salvo = veiculoRepository.save(veiculo);
        } catch (DataIntegrityViolationException e) {
            // Corrida entre a checagem acima e o insert: a constraint unique decide
            throw new PlacaDuplicadaException(placaNormalizada);
        }
        log.info("Veículo registrado: {} - {}", salvo.getPlaca(), salvo.getModelo());

        notificador.notificarNovoVeiculo(salvo);
        return salvo;
    }

    @Transactional
    public Veiculo atualizarVeiculo(Long id, String modelo, Integer anoFabricacao, Long quilometragem) {
        Veiculo veiculo = buscarPorId(id);

        if (modelo != null && !modelo.isEmpty()) {
            validator.validarModelo(modelo);
            veiculo.setModelo(modelo);
        }
        if (anoFabricacao != null) {
            validator.validarAnoFabricacao(anoFabricacao);
            veiculo.setAnoFabricacao(anoFabricacao);
        }
        if (quilometragem != null) {
            validator.validarQuilometragem(veiculo.getQuilometragem(), quilometragem);
            veiculo.setQuilometragem(quilometragem);
        }

        return veiculoRepository.save(veiculo);
    }

    // FIXME: Hardcoded status values, sem paginação
    @Transactional(readOnly = true)
    public List<Veiculo> obterVeiculosPorStatus(String status) {
        String normalizado = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizado == null || !STATUS_VALIDOS.contains(normalizado)) {
            throw new StatusVeiculoInvalidoException(status);
        }

        return veiculoRepository.findByStatus(normalizado);
    }

    // FIXME: não agenda nada, apenas loga e notifica
    @Transactional(readOnly = true)
    public void agendarManutencaoPreventiva(Long veiculoId, Long quilometragemLimite) {
        Veiculo veiculo = buscarPorId(veiculoId);

        log.info("Manutenção preventiva agendada para veículo {} em {} km",
            veiculo.getPlaca(), quilometragemLimite);

        notificador.notificarManutencaoAgendada(veiculo, quilometragemLimite);
    }
}
