package br.com.desafio.portabilidade.config;

import br.com.desafio.portabilidade.domain.*;
import br.com.desafio.portabilidade.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seed(AutorizacaoRepository a, ParcelaRepository p) {
        return args -> {
            if (!a.existsByBeneficiarioId("12345678900")) {
                a.save(new Autorizacao("12345678900", "CONV-001"));
                p.save(new Parcela("PAR-001", "12345678900", "CONV-001", new BigDecimal("245.90"), LocalDate.now().plusDays(10), StatusParcela.ABERTA));
                p.save(new Parcela("PAR-002", "12345678900", "CONV-001", new BigDecimal("245.90"), LocalDate.now().plusMonths(1), StatusParcela.ABERTA));
                p.save(new Parcela("PAR-003", "12345678900", "CONV-002", new BigDecimal("319.50"), LocalDate.now().plusMonths(2), StatusParcela.ABERTA));
            }
        };
    }
}
