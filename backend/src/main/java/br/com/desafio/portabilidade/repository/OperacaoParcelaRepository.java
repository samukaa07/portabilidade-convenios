package br.com.desafio.portabilidade.repository;

import br.com.desafio.portabilidade.domain.OperacaoParcela;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface OperacaoParcelaRepository extends JpaRepository<OperacaoParcela, Long> {
    List<OperacaoParcela> findByOperacaoId(String operacaoId);
}
