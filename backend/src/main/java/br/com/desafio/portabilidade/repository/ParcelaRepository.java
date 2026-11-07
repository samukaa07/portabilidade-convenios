package br.com.desafio.portabilidade.repository;

import br.com.desafio.portabilidade.domain.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.*;

public interface ParcelaRepository extends JpaRepository<Parcela, String> {
    List<Parcela> findByBeneficiarioIdOrderByDataDescontoAsc(String beneficiarioId);

    List<Parcela> findByIdIn(List<String> ids);

    /**
     * Bloqueio pessimista em ordem determinística (evita deadlock e dupla reserva da mesma parcela).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcela p where p.id in :ids order by p.id")
    List<Parcela> bloquearPorIds(@Param("ids") Collection<String> ids);
}
