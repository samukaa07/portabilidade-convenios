package br.com.desafio.portabilidade.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Backoff exponencial com teto e jitter: base * 2^(tentativa-1), limitado a max, ±jitter.
 */
@Component
public class BackoffPolicy {

    private final long baseMs, maxMs;
    private final double jitter;

    public BackoffPolicy(@Value("${central.backoff-base-ms:1000}") long baseMs,
                         @Value("${central.backoff-max-ms:30000}") long maxMs,
                         @Value("${central.backoff-jitter:0.2}") double jitter) {
        this.baseMs = baseMs;
        this.maxMs = maxMs;
        this.jitter = jitter;
    }

    public Duration proximo(int tentativa) {
        long exp = baseMs * (1L << Math.min(Math.max(tentativa - 1, 0), 20));
        long limitado = Math.min(exp, maxMs);
        double fator = jitter <= 0 ? 1.0 : 1.0 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
        return Duration.ofMillis(Math.max(1, Math.round(limitado * fator)));
    }
}
