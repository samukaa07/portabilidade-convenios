package br.com.desafio.portabilidade.web;

import br.com.desafio.portabilidade.domain.*;
import br.com.desafio.portabilidade.service.PortabilidadeService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PortabilidadeController {
    private final PortabilidadeService service;

    public PortabilidadeController(PortabilidadeService service) {
        this.service = service;
    }

    @PostMapping("/autorizacoes")
    public ResponseEntity<?> autorizar(@RequestBody PortabilidadeService.OptInRequest req) {
        return ResponseEntity.status(201).body(service.autorizar(req));
    }

    @GetMapping("/beneficiarios/{id}/agenda")
    public List<Parcela> agenda(@PathVariable String id) {
        return service.agenda(id);
    }

    @GetMapping("/beneficiarios/{id}/conta-recebimento")
    public Map<String, Object> conta(@PathVariable String id) {
        var c = service.contaRecebimento(id);
        return Map.of("beneficiarioId", c.getBeneficiarioId(), "conta", c.getConta(), "operacaoId", c.getOperacaoId(), "atualizadaEm", c.getAtualizadaEm());
    }

    @PostMapping("/operacoes-portabilidade")
    public ResponseEntity<?> criar(@RequestHeader("Idempotency-Key") String key, @RequestBody PortabilidadeService.CriarOperacaoRequest req) {
        Operacao op = service.criar(key, req);
        return ResponseEntity.status(op.getStatus() == StatusOperacao.FALHA_INTEGRACAO ? 502 : 202).body(op);
    }

    @GetMapping("/operacoes")
    public List<Operacao> listar() {
        return service.listar();
    }

    @GetMapping("/operacoes/{id}")
    public Map<String, Object> obter(@PathVariable String id) {
        Operacao op = service.obter(id);
        return Map.of("operacao", op, "parcelas", service.parcelasDaOperacao(id), "eventos", service.auditoria(id));
    }

    @GetMapping("/operacoes/{id}/auditoria")
    public List<EventoAuditoria> auditoria(@PathVariable String id) {
        return service.auditoria(id);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
