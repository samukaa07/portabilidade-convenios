package br.com.desafio.portabilidade.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssinaturaServiceTest {

    @Test
    void assinaturaEhDeterministicaEValida() {

        AssinaturaService s = new AssinaturaService("segredo-de-teste");
        String a = s.assinar("mensagem");
        assertEquals(a, s.assinar("mensagem"));
        assertTrue(s.validar("mensagem", a));
        assertFalse(s.validar("outra", a));
    }
}
