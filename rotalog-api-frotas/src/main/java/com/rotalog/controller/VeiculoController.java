package com.rotalog.controller;

import com.rotalog.domain.Veiculo;
import com.rotalog.dto.VeiculoRequest;
import com.rotalog.service.EstatisticasFrotaService;
import com.rotalog.service.VeiculoQuilometragemService;
import com.rotalog.service.VeiculoService;
import com.rotalog.service.VeiculoStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * VeiculoController - REST controller para gerenciamento de veículos.
 * Erros de domínio são mapeados por {@link com.rotalog.exception.GlobalExceptionHandler}.
 *
 * FIXME: Sem Swagger/OpenAPI
 * FIXME: Retornando entidade diretamente em vez de DTO de resposta
 * FIXME: Sem @Valid nos request bodies
 * FIXME: Sem paginação em listagens
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/veiculos")
public class VeiculoController {

    private final VeiculoService veiculoService;
    private final VeiculoStatusService veiculoStatusService;
    private final VeiculoQuilometragemService veiculoQuilometragemService;
    private final EstatisticasFrotaService estatisticasFrotaService;

    @GetMapping
    public ResponseEntity<List<Veiculo>> listarTodos() {
        return ResponseEntity.ok(veiculoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Veiculo> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(veiculoService.buscarPorId(id));
    }

    @GetMapping("/placa/{placa}")
    public ResponseEntity<Veiculo> buscarPorPlaca(@PathVariable String placa) {
        return ResponseEntity.ok(veiculoService.buscarPorPlaca(placa));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Veiculo>> listarPorStatus(@PathVariable String status) {
        return ResponseEntity.ok(veiculoService.obterVeiculosPorStatus(status));
    }

    @PostMapping
    public ResponseEntity<Veiculo> registrarVeiculo(@RequestBody VeiculoRequest request) {
        Veiculo veiculo = veiculoService.registrarVeiculo(
            request.getPlaca(),
            request.getModelo(),
            request.getAnoFabricacao()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(veiculo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Veiculo> atualizarVeiculo(@PathVariable Long id, @RequestBody VeiculoRequest request) {
        return ResponseEntity.ok(veiculoService.atualizarVeiculo(
            id,
            request.getModelo(),
            request.getAnoFabricacao(),
            request.getQuilometragem()
        ));
    }

    @PatchMapping("/{id}/quilometragem")
    public ResponseEntity<?> atualizarQuilometragem(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long quilometragem = body.get("quilometragem");
        if (quilometragem == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("erro", "Campo 'quilometragem' é obrigatório"));
        }
        return ResponseEntity.ok(veiculoQuilometragemService.atualizarQuilometragem(id, quilometragem));
    }

    @PatchMapping("/{id}/desativar")
    public ResponseEntity<Veiculo> desativarVeiculo(@PathVariable Long id) {
        return ResponseEntity.ok(veiculoStatusService.desativarVeiculo(id));
    }

    @PatchMapping("/{id}/reativar")
    public ResponseEntity<Veiculo> reativarVeiculo(@PathVariable Long id) {
        return ResponseEntity.ok(veiculoStatusService.reativarVeiculo(id));
    }

    /** FIXME: Retorna String JSON em vez de objeto tipado */
    @GetMapping("/estatisticas")
    public ResponseEntity<String> obterEstatisticas() {
        return ResponseEntity.ok(estatisticasFrotaService.obterEstatisticas());
    }
}
