package com.rotalog.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Resposta da api-notificacoes: status ENVIADO ou FALHA. */
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoStatusResponse {

    private Long id;
    private String status;
    private String erroMensagem;
}
