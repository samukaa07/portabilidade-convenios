package br.com.desafio.portabilidade.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "autorizacoes", uniqueConstraints = @UniqueConstraint(columnNames = {"beneficiarioId", "convenioId"}))
public class Autorizacao {

    @Id
    private String id;
    private String beneficiarioId;
    private String convenioId;
    private Instant criadaEm;

    protected Autorizacao() {

    }

    public Autorizacao(String beneficiarioId, String convenioId) {
        this.id = UUID.randomUUID().toString();
        this.beneficiarioId = beneficiarioId;
        this.convenioId = convenioId;
        this.criadaEm = Instant.now();
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

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
