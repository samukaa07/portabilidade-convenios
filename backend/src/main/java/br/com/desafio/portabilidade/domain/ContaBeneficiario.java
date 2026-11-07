package br.com.desafio.portabilidade.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Conta de recebimento vigente de um beneficiário; atualizada quando uma portabilidade é confirmada.
 */
@Entity
@Table(name = "contas_beneficiario")
public class ContaBeneficiario {

    @Id
    private String beneficiarioId;

    @Embedded
    private ContaRecebimento conta;
    private String operacaoId;
    private Instant atualizadaEm;

    protected ContaBeneficiario() {

    }

    public ContaBeneficiario(String beneficiarioId, ContaRecebimento conta, String operacaoId) {
        this.beneficiarioId = beneficiarioId;
        atualizar(conta, operacaoId);
    }

    public void atualizar(ContaRecebimento conta, String operacaoId) {
        this.conta = conta;
        this.operacaoId = operacaoId;
        this.atualizadaEm = Instant.now();
    }

    public String getBeneficiarioId() {
        return beneficiarioId;
    }

    public ContaRecebimento getConta() {
        return conta;
    }

    public String getOperacaoId() {
        return operacaoId;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }
}
