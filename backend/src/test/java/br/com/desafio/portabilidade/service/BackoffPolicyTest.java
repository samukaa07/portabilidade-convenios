package br.com.desafio.portabilidade.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BackoffPolicyTest {

    @Test
    void cresceExponencialmenteELimitaNoTeto() {
        BackoffPolicy b = new BackoffPolicy(1000, 10000, 0);
        assertEquals(Duration.ofSeconds(1), b.proximo(1));
        assertEquals(Duration.ofSeconds(2), b.proximo(2));
        assertEquals(Duration.ofSeconds(4), b.proximo(3));
        assertEquals(Duration.ofSeconds(8), b.proximo(4));
        assertEquals(Duration.ofSeconds(10), b.proximo(5));
        assertEquals(Duration.ofSeconds(10), b.proximo(50));
    }

    @Test
    void jitterMantemValorDentroDaFaixa() {
        BackoffPolicy b = new BackoffPolicy(1000, 30000, 0.2);
        for (int i = 0; i < 100; i++) {
            long ms = b.proximo(3).toMillis();
            assertTrue(ms >= 3200 && ms <= 4800, "fora da faixa: " + ms);
        }
    }
}
