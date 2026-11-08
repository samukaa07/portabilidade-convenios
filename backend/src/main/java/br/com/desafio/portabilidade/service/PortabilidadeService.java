package br.com.desafio.portabilidade.service;

import br.com.desafio.portabilidade.domain.*;
import br.com.desafio.portabilidade.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class PortabilidadeService {

    public record OptInRequest(String beneficiarioId, String convenioId) {
    }

    public record ContaRequest(String banco, String agencia, String conta) {
    }

    public record CriarOperacaoRequest(String beneficiarioId, List<String> parcelas, String credorCessionario,
                                       ContaRequest contaRecebimento) {
    }

    private record Reserva(Operacao operacao, boolean nova) {
    }

    private final AutorizacaoRepository autorizacoes;
    private final ParcelaRepository parcelas;
    private final OperacaoRepository operacoes;
    private final OperacaoParcelaRepository operacaoParcelas;
    private final EventoAuditoriaRepository eventos;
    private final ContaBeneficiarioRepository contas;
    private final CentralGateway central;
    private final AssinaturaService assinatura;
    private final BackoffPolicy backoff;
    private final TransactionTemplate tx;
    private final int maxTentativasConsulta;

    public PortabilidadeService(AutorizacaoRepository a, ParcelaRepository p, OperacaoRepository o, OperacaoParcelaRepository op, EventoAuditoriaRepository e,
                                ContaBeneficiarioRepository contas, CentralGateway c, AssinaturaService s, BackoffPolicy backoff, PlatformTransactionManager tm,
                                @Value("${central.polling-max-attempts:12}") int maxTentativasConsulta) {
        autorizacoes = a;
        parcelas = p;
        operacoes = o;
        operacaoParcelas = op;
        eventos = e;
        this.contas = contas;
        central = c;
        assinatura = s;
        this.backoff = backoff;
        tx = new TransactionTemplate(tm);
        this.maxTentativasConsulta = maxTentativasConsulta;
    }

    @Transactional
    public Autorizacao autorizar(OptInRequest req) {
        if (req == null || isBlank(req.beneficiarioId()) || isBlank(req.convenioId()))
            throw new IllegalArgumentException("beneficiarioId e convenioId são obrigatórios");
        return autorizacoes.findByBeneficiarioIdAndConvenioId(req.beneficiarioId(), req.convenioId())
                .orElseGet(() -> autorizacoes.save(new Autorizacao(req.beneficiarioId(), req.convenioId())));
    }

    public List<Parcela> agenda(String beneficiarioId) {
        if (!autorizacoes.existsByBeneficiarioId(beneficiarioId))
            throw new IllegalStateException("Beneficiário não possui autorização (opt-in)");
        return parcelas.findByBeneficiarioIdOrderByDataDescontoAsc(beneficiarioId);
    }

    /**
     * Fluxo idempotente: reserva local (transação curta) -> chamada à Central fora da transação -> registro do protocolo/falha.
     */
    public Operacao criar(String key, CriarOperacaoRequest req) {
        validar(key, req);
        List<String> ids = req.parcelas().stream().distinct().sorted().toList();
        String hash = sha256(SolicitacaoCentral.payloadCanonico(req.beneficiarioId(), ids, req.credorCessionario(), conta(req)));
        Reserva reserva;
        try {
            reserva = tx.execute(s -> reservar(key, hash, ids, req));
        } catch (DataIntegrityViolationException ex) {
            reserva = new Reserva(existente(key, hash), false);
        }
        if (!reserva.nova()) return reserva.operacao();
        return enviarParaCentral(reserva.operacao().getId(), key, ids, req);
    }

    private Reserva reservar(String key, String hash, List<String> ids, CriarOperacaoRequest req) {
        List<Parcela> selecionadas = parcelas.bloquearPorIds(ids);
        Optional<Operacao> existente = operacoes.findByIdempotencyKey(key);
        if (existente.isPresent()) return new Reserva(conferirHash(existente.get(), hash), false);
        if (!autorizacoes.existsByBeneficiarioId(req.beneficiarioId()))
            throw new IllegalStateException("Beneficiário não autorizado");
        if (selecionadas.size() != ids.size() || selecionadas.stream().anyMatch(p -> !p.getBeneficiarioId().equals(req.beneficiarioId()) || p.getStatus() != StatusParcela.ABERTA))
            throw new IllegalArgumentException("Uma ou mais parcelas não existem, não pertencem ao beneficiário ou não estão elegíveis");
        Operacao op = operacoes.saveAndFlush(new Operacao(key, hash, req.beneficiarioId(), req.credorCessionario(),
                new ContaRecebimento(req.contaRecebimento().banco(), req.contaRecebimento().agencia(), req.contaRecebimento().conta())));
        selecionadas.forEach(p -> p.setStatus(StatusParcela.EM_PORTABILIDADE));
        operacaoParcelas.saveAll(ids.stream().map(id -> new OperacaoParcela(op.getId(), id)).toList());
        eventos.save(new EventoAuditoria(op.getId(), "SOLICITADA", "Portabilidade solicitada para as parcelas " + ids + " com conta " + req.contaRecebimento().banco() + "/" + req.contaRecebimento().agencia() + "/" + req.contaRecebimento().conta()));
        return new Reserva(op, true);
    }

    private Operacao enviarParaCentral(String operacaoId, String key, List<String> ids, CriarOperacaoRequest req) {
        SolicitacaoCentral.Conta conta = conta(req);
        String assinada = assinatura.assinar(SolicitacaoCentral.payloadCanonico(req.beneficiarioId(), ids, req.credorCessionario(), conta));
        try {
            String protocolo = central.registrar(key, new SolicitacaoCentral(req.beneficiarioId(), ids, req.credorCessionario(), conta, assinada));
            tx.executeWithoutResult(s -> {
                Operacao op = operacoes.findById(operacaoId).orElseThrow();
                op.registrarProtocolo(protocolo);
                eventos.save(new EventoAuditoria(op.getId(), "PROTOCOLO_RECEBIDO", "Central aceitou a solicitação (202). Protocolo: " + protocolo));
            });
        } catch (CentralIntegracaoException ex) {
            tx.executeWithoutResult(s -> finalizar(operacoes.findById(operacaoId).orElseThrow(), StatusOperacao.FALHA_INTEGRACAO, ex.getMessage(), "FALHA_INTEGRACAO", ex.getMessage()));
        }
        return operacoes.findById(operacaoId).orElseThrow();
    }

    private Operacao existente(String key, String hash) {
        return conferirHash(operacoes.findByIdempotencyKey(key).orElseThrow(() -> new IllegalStateException("Conflito ao registrar operação; tente novamente")), hash);
    }

    private Operacao conferirHash(Operacao op, String hash) {
        if (op.getPayloadHash() != null && !op.getPayloadHash().equals(hash))
            throw new IllegalStateException("Idempotency-Key já utilizada com uma solicitação diferente");
        return op;
    }

    private void validar(String key, CriarOperacaoRequest req) {
        if (isBlank(key)) throw new IllegalArgumentException("Header Idempotency-Key é obrigatório");
        if (req == null || isBlank(req.beneficiarioId()))
            throw new IllegalArgumentException("beneficiarioId é obrigatório");
        if (req.parcelas() == null || req.parcelas().isEmpty() || req.parcelas().stream().anyMatch(this::isBlank))
            throw new IllegalArgumentException("Selecione ao menos uma parcela");
        if (isBlank(req.credorCessionario())) throw new IllegalArgumentException("credorCessionario é obrigatório");
        ContaRequest c = req.contaRecebimento();
        if (c == null || isBlank(c.banco()) || isBlank(c.agencia()) || isBlank(c.conta()))
            throw new IllegalArgumentException("Conta de recebimento (banco, agência e conta) é obrigatória");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private SolicitacaoCentral.Conta conta(CriarOperacaoRequest r) {
        return new SolicitacaoCentral.Conta(r.contaRecebimento().banco(), r.contaRecebimento().agencia(), r.contaRecebimento().conta());
    }

    private String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Consulta a Central para as operações pendentes cujo próximo horário (backoff) já venceu.
     */
    public void atualizarPendentes() {
        for (Operacao op : operacoes.findByStatusAndProtocoloIsNotNullAndProximaConsultaEmLessThanEqualOrderByCriadaEmAsc(StatusOperacao.PENDENTE, Instant.now()))
            processarPendente(op.getId(), op.getProtocolo());
    }

    public void processarPendente(String operacaoId, String protocolo) {
        CentralGateway.ConsultaCentral consulta = null;
        CentralIntegracaoException erro = null;
        try {
            consulta = central.consultar(protocolo);
        } catch (CentralIntegracaoException e) {
            erro = e;
        }
        final CentralGateway.ConsultaCentral c = consulta;
        final CentralIntegracaoException falha = erro;
        tx.executeWithoutResult(s -> {
            Operacao op = operacoes.findById(operacaoId).orElseThrow();
            if (op.getStatus() != StatusOperacao.PENDENTE) return;
            if (falha != null) {
                eventos.save(new EventoAuditoria(op.getId(), "ERRO_CONSULTA_CENTRAL", falha.getMessage() + " (tentativa " + (op.getTentativasConsulta() + 1) + ")"));
                reagendar(op, null);
            } else if (c.status() == StatusOperacao.PENDENTE) {
                reagendar(op, c.retryAfterSegundos());
            } else {
                String detalhe = c.status() == StatusOperacao.CONFIRMADA ? "Central confirmou a portabilidade" : "Central rejeitou: " + c.motivo();
                finalizar(op, c.status(), c.motivo(), c.status().name(), detalhe);
            }
        });
    }

    private void reagendar(Operacao op, Integer retryAfterSegundos) {
        int tentativa = op.getTentativasConsulta() + 1;
        if (tentativa >= maxTentativasConsulta) {
            String msg = "Confirmação não obtida após " + tentativa + " consultas à Central";
            finalizar(op, StatusOperacao.FALHA_INTEGRACAO, msg, "FALHA_INTEGRACAO", msg);
            return;
        }
        Duration espera = backoff.proximo(tentativa);
        if (retryAfterSegundos != null && Duration.ofSeconds(retryAfterSegundos).compareTo(espera) > 0)
            espera = Duration.ofSeconds(retryAfterSegundos);
        op.agendarProximaConsulta(Instant.now().plus(espera));
    }

    /**
     * Única porta de saída de PENDENTE: muda estado, ajusta parcelas/conta e acrescenta evento de auditoria.
     */
    private void finalizar(Operacao op, StatusOperacao novo, String motivo, String evento, String detalhe) {
        op.atualizar(novo, motivo);
        boolean confirmada = novo == StatusOperacao.CONFIRMADA;
        for (OperacaoParcela item : operacaoParcelas.findByOperacaoId(op.getId()))
            parcelas.findById(item.getParcelaId()).ifPresent(p -> p.setStatus(confirmada ? StatusParcela.PORTABILIZADA : StatusParcela.ABERTA));
        eventos.save(new EventoAuditoria(op.getId(), evento, detalhe));
        if (confirmada) {
            ContaRecebimento nova = op.getContaRecebimento();
            contas.findById(op.getBeneficiarioId()).ifPresentOrElse(c -> c.atualizar(nova, op.getId()), () -> contas.save(new ContaBeneficiario(op.getBeneficiarioId(), nova, op.getId())));
            eventos.save(new EventoAuditoria(op.getId(), "CONTA_RECEBIMENTO_ATUALIZADA", "Conta de recebimento do beneficiário atualizada para " + nova.getBanco() + "/" + nova.getAgencia() + "/" + nova.getConta()));
        }
    }

    public ContaBeneficiario contaRecebimento(String beneficiarioId) {
        return contas.findById(beneficiarioId).orElseThrow(() -> new NoSuchElementException("Beneficiário sem conta de recebimento atualizada"));
    }

    public List<Operacao> listar() {
        return operacoes.findAllByOrderByCriadaEmDesc();
    }

    public Operacao obter(String id) {
        return operacoes.findById(id).orElseThrow(() -> new NoSuchElementException("Operação não encontrada"));
    }

    public List<EventoAuditoria> auditoria(String id) {
        obter(id);
        return eventos.findByOperacaoIdOrderByOcorridoEmAscIdAsc(id);
    }

    public List<String> parcelasDaOperacao(String id) {
        return operacaoParcelas.findByOperacaoId(id).stream().map(OperacaoParcela::getParcelaId).toList();
    }
}
