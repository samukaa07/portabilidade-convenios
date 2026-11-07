package br.com.desafio.portabilidade.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operacoes", uniqueConstraints = @UniqueConstraint(columnNames = "idempotencyKey"))
public class Operacao {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;
    private String payloadHash;
    private String beneficiarioId;
    private String protocolo;
    private String credorCessionario;

    @Embedded
    private ContaRecebimento contaRecebimento;

    @Enumerated(EnumType.STRING)
    private StatusOperacao status;

    @Column(length = 1000)
    private String motivoRejeicao;
    private Instant criadaEm;
    private Instant atualizadaEm;
    private Integer tentativasConsulta;
    private Instant proximaConsultaEm;

    protected Operacao() {
    }

    public Operacao(String key, String payloadHash, String beneficiarioId, String credor, ContaRecebimento conta) {
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = key;
        this.payloadHash = payloadHash;
        this.beneficiarioId = beneficiarioId;
        this.credorCessionario = credor;
        this.contaRecebimento = conta;
        this.status = StatusOperacao.PENDENTE;
        this.criadaEm = Instant.now();
        this.atualizadaEm = this.criadaEm;
        this.tentativasConsulta = 0;
        this.proximaConsultaEm = this.criadaEm;
    }

    public void registrarProtocolo(String protocolo) {
        this.protocolo = protocolo;
        this.atualizadaEm = Instant.now();
    }

    public void atualizar(StatusOperacao novo, String motivo) {
        if (!status.podeTransitarPara(novo))
            throw new IllegalStateException("Transição inválida de " + status + " para " + novo);
        this.status = novo;
        this.motivoRejeicao = motivo;
        this.atualizadaEm = Instant.now();
        this.proximaConsultaEm = null;
    }

    public void agendarProximaConsulta(Instant quando) {
        this.tentativasConsulta = getTentativasConsulta() + 1;
        this.proximaConsultaEm = quando;
        this.atualizadaEm = Instant.now();
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }

    public int getTentativasConsulta() {
        return tentativasConsulta == null ? 0 : tentativasConsulta;
    }

    public Instant getProximaConsultaEm() {
        return proximaConsultaEm;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getBeneficiarioId() {
        return beneficiarioId;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public String getCredorCessionario() {
        return credorCessionario;
    }

    public ContaRecebimento getContaRecebimento() {
        return contaRecebimento;
    }

    public StatusOperacao getStatus() {
        return status;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
