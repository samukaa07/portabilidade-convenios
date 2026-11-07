package br.com.desafio.portabilidade.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "parcelas")
public class Parcela {

    @Id
    private String id;
    private String beneficiarioId;
    private String convenioId;
    private BigDecimal valor;
    private LocalDate dataDesconto;

    @Enumerated(EnumType.STRING)
    private StatusParcela status;

    protected Parcela() {
    }

    public Parcela(String id, String beneficiarioId, String convenioId, BigDecimal valor, LocalDate dataDesconto, StatusParcela status) {
        this.id = id;
        this.beneficiarioId = beneficiarioId;
        this.convenioId = convenioId;
        this.valor = valor;
        this.dataDesconto = dataDesconto;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getBeneficiarioId() {
        return beneficiarioId;
    }

    public String getConvenioId() {
        return convenioId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getDataDesconto() {
        return dataDesconto;
    }

    public StatusParcela getStatus() {
        return status;
    }

    public void setStatus(StatusParcela status) {
        this.status = status;
    }
}
