package br.com.desafio.portabilidade.repository;

import br.com.desafio.portabilidade.domain.EventoAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {
    List<EventoAuditoria> findByOperacaoIdOrderByOcorridoEmAscIdAsc(String operacaoId);
}
