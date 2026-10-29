package com.rotalog.controller;

import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.service.AlertaManutencaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Alertas de manutenção preventiva. Erros de domínio são mapeados por
 * {@link com.rotalog.exception.GlobalExceptionHandler}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/alertas")
public class AlertaManutencaoController {

    private final AlertaManutencaoService alertaService;

    @GetMapping
    public ResponseEntity<List<AlertaManutencaoResponse>> listar(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(alertaService.listar(status).stream()
                .map(AlertaManutencaoResponse::de)
                .collect(Collectors.toList()));
    }

    @PostMapping("/verificar")
    public ResponseEntity<List<AlertaManutencaoResponse>> verificar() {
        return ResponseEntity.ok(alertaService.verificarEEnviar().stream()
                .map(AlertaManutencaoResponse::de)
                .collect(Collectors.toList()));
    }
}
