package br.com.desafio.portabilidade.service;

import br.com.desafio.portabilidade.domain.StatusOperacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado em memória da Central simulada: protocolos, atraso de confirmação, rejeição e falhas ocasionais.
 */
@Service
public class CentralMockService {

    public enum Falha {NENHUMA, INDISPONIVEL, TIMEOUT}

    public record Resultado(String protocolo, StatusOperacao status, String motivo) {
    }

    private record Registro(Instant criadoEm, boolean rejeitar) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Map<String, String> protocolosPorChave = new ConcurrentHashMap<>();
    @Value("${central.confirmation-delay-ms:8000}")
    private long delayMs;
    @Value("${central.failure-rate:0.15}")
    private double rejectionRate;
    @Value("${central.unavailable-rate:0.10}")
    private double unavailableRate;
    @Value("${central.timeout-rate:0.05}")
    private double timeoutRate;
    @Value("${central.timeout-sleep-ms:3000}")
    private long timeoutSleepMs;

    public boolean assinaturaValida(SolicitacaoCentral s) {
        return s != null && s.parcelas() != null && s.contaRecebimento() != null && s.beneficiarioId() != null && assinaturaService.validar(s.payloadCanonico(), s.assinatura());
    }

    /**
     * Idempotente: a mesma Idempotency-Key devolve sempre o mesmo protocolo.
     */
    public String registrar(String idempotencyKey) {
        return protocolosPorChave.computeIfAbsent(idempotencyKey, k -> {
            String protocolo = "CTR-" + UUID.randomUUID();
            registros.put(protocolo, new Registro(Instant.now(), Math.random() < rejectionRate));
            return protocolo;
        });
    }

    public Falha sortearFalha(boolean admiteTimeout) {
        double x = Math.random();
        if (admiteTimeout && x < timeoutRate) return Falha.TIMEOUT;
        if (Math.random() < unavailableRate) return Falha.INDISPONIVEL;
        return Falha.NENHUMA;
    }

    public long getTimeoutSleepMs() {
        return timeoutSleepMs;
    }

    private final AssinaturaService assinaturaService;

    public CentralMockService(AssinaturaService assinaturaService) {
        this.assinaturaService = assinaturaService;
    }

    public Resultado consultar(String protocolo) {
        Registro r = registros.get(protocolo);
        if (r == null) throw new NoSuchElementException("Protocolo não encontrado na Central");
        if (Instant.now().isBefore(r.criadoEm().plusMillis(delayMs)))
            return new Resultado(protocolo, StatusOperacao.PENDENTE, null);
        return r.rejeitar() ? new Resultado(protocolo, StatusOperacao.REJEITADA, "Operação rejeitada pela Central (simulação)")
                : new Resultado(protocolo, StatusOperacao.CONFIRMADA, null);
    }
}
