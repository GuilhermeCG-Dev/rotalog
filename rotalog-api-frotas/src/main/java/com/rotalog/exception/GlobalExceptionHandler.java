package com.rotalog.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Mapeia a hierarquia {@link RotalogException} para status HTTP, mantendo o corpo {"erro": "..."}.
 * Erros inesperados não são tratados aqui: o Spring responde 500 sem expor a mensagem.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(VeiculoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(RotalogException e) {
        return resposta(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler({PlacaDuplicadaException.class, TransicaoStatusInvalidaException.class})
    public ResponseEntity<Map<String, String>> conflito(RotalogException e) {
        return resposta(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(QuilometragemInvalidaException.class)
    public ResponseEntity<Map<String, String>> quilometragemInvalida(RotalogException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler({PlacaInvalidaException.class, DadoVeiculoInvalidoException.class,
            StatusVeiculoInvalidoException.class, StatusAlertaInvalidoException.class})
    public ResponseEntity<Map<String, String>> requisicaoInvalida(RotalogException e) {
        return resposta(HttpStatus.BAD_REQUEST, e);
    }

    private ResponseEntity<Map<String, String>> resposta(HttpStatus status, RotalogException e) {
        return ResponseEntity.status(status).body(Map.of("erro", e.getMessage()));
    }
}
