package br.com.desafio.portabilidade.service;

import java.util.List;

/**
 * Mensagem enviada à Central para registrar a portabilidade (contrato: POST /operacoes-portabilidade).
 */
public record SolicitacaoCentral(String beneficiarioId, List<String> parcelas, String credorCessionario,
                                 Conta contaRecebimento, String assinatura) {

    public record Conta(String banco, String agencia, String conta) {
    }

    /**
     * Forma canônica usada para assinar/verificar (independe da ordem das parcelas).
     */
    public static String payloadCanonico(String beneficiarioId, List<String> parcelas, String credor, Conta c) {
        return beneficiarioId + "|" + String.join(",", parcelas.stream().sorted().toList()) + "|" + credor
                + "|" + c.banco() + "|" + c.agencia() + "|" + c.conta();
    }

    public String payloadCanonico() {
        return payloadCanonico(beneficiarioId, parcelas, credorCessionario, contaRecebimento);
    }
}
