package br.com.desafio.portabilidade.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "eventos_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String operacaoId;
    private String evento;

    @Column(length = 2000)
    private String detalhe;
    private Instant ocorridoEm;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(String operacaoId, String evento, String detalhe) {
        this.operacaoId = operacaoId;
        this.evento = evento;
        this.detalhe = detalhe;
        this.ocorridoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOperacaoId() {
        return operacaoId;
    }

    public String getEvento() {
        return evento;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
