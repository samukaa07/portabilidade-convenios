package br.com.desafio.portabilidade.service;

import br.com.desafio.portabilidade.domain.StatusOperacao;

/**
 * Porta de integração com a Central de Convênios.
 */
public interface CentralGateway {

    record ConsultaCentral(StatusOperacao status, String motivo, Integer retryAfterSegundos) {
    }

    /**
     * Registra a operação e devolve o protocolo. Idempotente na Central pela chave informada.
     */
    String registrar(String idempotencyKey, SolicitacaoCentral solicitacao);

    ConsultaCentral consultar(String protocolo);
}
