package br.com.desafio.portabilidade.repository;

import br.com.desafio.portabilidade.domain.Autorizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface AutorizacaoRepository extends JpaRepository<Autorizacao, String> {
    Optional<Autorizacao> findByBeneficiarioIdAndConvenioId(String beneficiarioId, String convenioId);

    boolean existsByBeneficiarioId(String beneficiarioId);
}
