package br.com.desafio.portabilidade.web;

import br.com.desafio.portabilidade.domain.Autorizacao;
import br.com.desafio.portabilidade.domain.StatusOperacao;
import br.com.desafio.portabilidade.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Central de Convênios simulada (sistema externo). Segue o contrato mínimo do desafio:
 * 202 + protocolo no registro, 204 + Retry-After enquanto pendente, assinatura HMAC nas mensagens
 * e falhas ocasionais (503, timeout, rejeição). Acessada pelo backend via HTTP real (CentralHttpClient).
 */
@RestController
@RequestMapping("/central")
public class CentralMockController {
    private final CentralMockService central;
    private final AssinaturaService assinatura;
    private final PortabilidadeService service;

    public CentralMockController(CentralMockService central, AssinaturaService assinatura, PortabilidadeService service) {
        this.central = central;
        this.assinatura = assinatura;
        this.service = service;
    }

    @PostMapping("/autorizacoes")
    public ResponseEntity<?> autorizar(@RequestBody PortabilidadeService.OptInRequest req) {
        Autorizacao a = service.autorizar(req);
        return ResponseEntity.status(201).body(Map.of("autorizacaoId", a.getId()));
    }

    @GetMapping("/beneficiarios/{id}/agenda")
    public List<Map<String, Object>> agenda(@PathVariable String id) {
        return service.agenda(id).stream().map(p -> Map.<String, Object>of("parcelaId", p.getId(), "convenioId", p.getConvenioId(),
                "valor", p.getValor(), "dataDesconto", p.getDataDesconto().toString(), "status", p.getStatus().name())).toList();
    }

    @PostMapping("/operacoes-portabilidade")
    public ResponseEntity<?> registrar(@RequestHeader("Idempotency-Key") String key, @RequestBody SolicitacaoCentral req) {
        if (!central.assinaturaValida(req))
            return ResponseEntity.status(401).body(Map.of("erro", "Assinatura inválida"));
        String protocolo = central.registrar(key);
        CentralMockService.Falha falha = central.sortearFalha(true);
        if (falha == CentralMockService.Falha.TIMEOUT) dormir(central.getTimeoutSleepMs());
        if (falha == CentralMockService.Falha.INDISPONIVEL)
            return ResponseEntity.status(503).body(Map.of("erro", "Central temporariamente indisponível"));
        return ResponseEntity.accepted().header("X-Assinatura", assinatura.assinar(protocolo)).body(Map.of("protocolo", protocolo));
    }

    @GetMapping("/operacoes-portabilidade/{protocolo}")
    public ResponseEntity<?> status(@PathVariable String protocolo) {
        if (central.sortearFalha(false) == CentralMockService.Falha.INDISPONIVEL)
            return ResponseEntity.status(503).body(Map.of("erro", "Central temporariamente indisponível"));
        CentralMockService.Resultado r = central.consultar(protocolo);
        if (r.status() == StatusOperacao.PENDENTE)
            return ResponseEntity.noContent().header(HttpHeaders.RETRY_AFTER, "2").build();
        String assinado = protocolo + "|" + r.status() + "|" + (r.motivo() == null ? "" : r.motivo());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", r.status().name());
        corpo.put("motivoRejeicao", r.motivo());
        return ResponseEntity.ok().header("X-Assinatura", assinatura.assinar(assinado)).body(corpo);
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
