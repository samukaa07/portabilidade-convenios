package br.com.desafio.portabilidade.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "operacao_parcelas")
public class OperacaoParcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String operacaoId;
    private String parcelaId;

    protected OperacaoParcela() {
    }

    public OperacaoParcela(String operacaoId, String parcelaId) {
        this.operacaoId = operacaoId;
        this.parcelaId = parcelaId;
    }

    public String getParcelaId() {
        return parcelaId;
    }
}
