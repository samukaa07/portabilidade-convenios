package br.com.desafio.portabilidade.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public class ContaRecebimento {
    private String banco;
    private String agencia;
    private String conta;

    protected ContaRecebimento() {
    }

    public ContaRecebimento(String banco, String agencia, String conta) {
        this.banco = banco;
        this.agencia = agencia;
        this.conta = conta;
    }

    public String getBanco() {
        return banco;
    }

    public String getAgencia() {
        return agencia;
    }

    public String getConta() {
        return conta;
    }
}
