package br.com.desafio.portabilidade.repository;

import br.com.desafio.portabilidade.domain.ContaBeneficiario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaBeneficiarioRepository extends JpaRepository<ContaBeneficiario, String> {
}
