package br.com.desafio.portabilidade.repository;

import br.com.desafio.portabilidade.domain.Operacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface OperacaoRepository extends JpaRepository<Operacao, String> {

    Optional<Operacao> findByIdempotencyKey(String key);

    List<Operacao> findAllByOrderByCriadaEmDesc();

    List<Operacao> findByStatusAndProtocoloIsNotNullAndProximaConsultaEmLessThanEqualOrderByCriadaEmAsc(br.com.desafio.portabilidade.domain.StatusOperacao status, java.time.Instant ate);
}
