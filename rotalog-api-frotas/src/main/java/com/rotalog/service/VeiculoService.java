package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import com.rotalog.service.validation.VeiculoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consulta, cadastro e atualização de veículos. Status, quilometragem, estatísticas,
 * validação, política de manutenção e notificação ficam em classes próprias.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VeiculoService {

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
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado: " + id)); // FIXME: Use proper exception
    }

    @Transactional(readOnly = true)
    public Veiculo buscarPorPlaca(String placa) {
        return veiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado com placa: " + placa));
    }

    @Transactional
    public Veiculo registrarVeiculo(String placa, String modelo, Integer anoFabricacao) {
        validator.validarPlaca(placa);

        if (veiculoRepository.findByPlaca(placa).isPresent()) {
            throw new RuntimeException("Veículo com placa " + placa + " já existe"); // FIXME: Use proper exception
        }

        validator.validarModelo(modelo);
        validator.validarAnoFabricacao(anoFabricacao);

        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa.toUpperCase());
        veiculo.setModelo(modelo);
        veiculo.setAnoFabricacao(anoFabricacao);
        veiculo.setStatus("ATIVO");
        veiculo.setQuilometragem(0L);

        Veiculo salvo = veiculoRepository.save(veiculo);
        log.info("Veículo registrado: {} - {}", salvo.getPlaca(), salvo.getModelo());

        notificador.notificarNovoVeiculo(salvo);
        return salvo;
    }

    @Transactional
    public Veiculo atualizarVeiculo(Long id, String modelo, Integer anoFabricacao, Long quilometragem) {
        Veiculo veiculo = buscarPorId(id);

        if (modelo != null && !modelo.isEmpty()) {
            veiculo.setModelo(modelo);
        }
        if (anoFabricacao != null) {
            veiculo.setAnoFabricacao(anoFabricacao);
        }
        if (quilometragem != null) {
            if (quilometragem < veiculo.getQuilometragem()) {
                // FIXME: deveria lançar exceção, não apenas logar
                log.warn("Tentativa de reduzir quilometragem do veículo {}: {} -> {}",
                    id, veiculo.getQuilometragem(), quilometragem);
            }
            veiculo.setQuilometragem(quilometragem);
        }

        return veiculoRepository.save(veiculo);
    }

    // FIXME: Hardcoded status values, sem paginação
    @Transactional(readOnly = true)
    public List<Veiculo> obterVeiculosPorStatus(String status) {
        if (!status.equals("ATIVO") && !status.equals("INATIVO") && !status.equals("MANUTENCAO")) {
            throw new RuntimeException("Status inválido: " + status); // FIXME: Use proper exception
        }

        return veiculoRepository.findByStatus(status);
    }

    // FIXME: não agenda nada, apenas loga e notifica
    @Transactional(readOnly = true)
    public void agendarManutencaoPreventiva(Long veiculoId, Long quilometragemLimite) {
        Veiculo veiculo = buscarPorId(veiculoId);

        log.info("Manutenção preventiva agendada para veículo {} em {} km",
            veiculo.getPlaca(), quilometragemLimite);

        notificador.notificarManutencaoAgendada(veiculo, quilometragemLimite);
    }

    // FIXME: stub sem chamador
    public void sincronizarComSistemaExterno() {
        log.info("Sincronização com sistema externo iniciada");
        System.out.println("Sincronização iniciada"); // FIXME: Use logger
    }
}
