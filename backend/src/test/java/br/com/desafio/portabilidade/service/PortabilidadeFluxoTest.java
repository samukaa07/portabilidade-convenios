package br.com.desafio.portabilidade.service;

import br.com.desafio.portabilidade.domain.*;
import br.com.desafio.portabilidade.repository.*;
import br.com.desafio.portabilidade.service.CentralIntegracaoException.Tipo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fluxo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "central.polling-enabled=false",
        "central.polling-max-attempts=3"})

class PortabilidadeFluxoTest {

    @Autowired
    PortabilidadeService service;
    @Autowired
    OperacaoRepository operacoes;
    @Autowired
    ParcelaRepository parcelas;
    @Autowired
    AutorizacaoRepository autorizacoes;
    @Autowired
    EventoAuditoriaRepository eventos;
    @Autowired
    ContaBeneficiarioRepository contas;
    @MockBean
    CentralGateway central;

    String beneficiario, p1, p2;

    @BeforeEach
    void preparar() {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        beneficiario = "B-" + sufixo;
        p1 = "P1-" + sufixo;
        p2 = "P2-" + sufixo;
        autorizacoes.save(new Autorizacao(beneficiario, "CONV-X"));
        parcelas.save(new Parcela(p1, beneficiario, "CONV-X", new BigDecimal("100.00"), LocalDate.now().plusDays(5), StatusParcela.ABERTA));
        parcelas.save(new Parcela(p2, beneficiario, "CONV-X", new BigDecimal("200.00"), LocalDate.now().plusDays(35), StatusParcela.ABERTA));
    }

    PortabilidadeService.CriarOperacaoRequest req(String... ids) {
        return new PortabilidadeService.CriarOperacaoRequest(beneficiario, List.of(ids), "Credor Novo",
                new PortabilidadeService.ContaRequest("001", "1234", "99999-0"));
    }

    List<String> eventosDe(Operacao op) {
        return eventos.findByOperacaoIdOrderByOcorridoEmAscIdAsc(op.getId()).stream().map(EventoAuditoria::getEvento).toList();
    }

    StatusParcela statusParcela(String id) {
        return parcelas.findById(id).orElseThrow().getStatus();
    }

    // ---------- idempotência ----------

    @Test
    void reenvioComMesmaChaveNaoDuplicaOperacaoNemChamaCentralDeNovo() {
        when(central.registrar(eq("k1"), any())).thenReturn("CTR-1");
        Operacao a = service.criar("k1", req(p1));
        Operacao b = service.criar("k1", req(p1));
        assertThat(b.getId()).isEqualTo(a.getId());
        assertThat(operacoes.findAll().stream().filter(o -> o.getBeneficiarioId().equals(beneficiario))).hasSize(1);
        verify(central, times(1)).registrar(eq("k1"), any());
        assertThat(eventosDe(a)).containsExactly("SOLICITADA", "PROTOCOLO_RECEBIDO");
    }

    @Test
    void requisicoesConcorrentesComMesmaChaveGeramUmaUnicaOperacao() throws Exception {
        when(central.registrar(eq("kc"), any())).thenAnswer(i -> {
            Thread.sleep(200);
            return "CTR-C";
        });
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Operacao>> futuros = new ArrayList<>();
        CountDownLatch largada = new CountDownLatch(1);
        for (int i = 0; i < 6; i++)
            futuros.add(pool.submit(() -> {
                largada.await();
                return service.criar("kc", req(p1, p2));
            }));
        largada.countDown();
        Set<String> ids = new HashSet<>();
        for (Future<Operacao> f : futuros) ids.add(f.get(20, TimeUnit.SECONDS).getId());
        pool.shutdown();
        assertThat(ids).hasSize(1);
        verify(central, times(1)).registrar(eq("kc"), any());
    }

    @Test
    void mesmaChaveComPayloadDiferenteEhRecusada() {
        when(central.registrar(any(), any())).thenReturn("CTR-2");
        service.criar("k2", req(p1));
        assertThatThrownBy(() -> service.criar("k2", req(p2))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Idempotency-Key");
    }

    @Test
    void parcelaJaEmPortabilidadeNaoPodeSerReservadaPorOutraChave() {
        when(central.registrar(any(), any())).thenReturn("CTR-3");
        service.criar("k3a", req(p1));
        assertThatThrownBy(() -> service.criar("k3b", req(p1))).isInstanceOf(IllegalArgumentException.class);
        verify(central, times(1)).registrar(any(), any());
    }

    @Test
    void chaveAusenteEhRecusada() {
        assertThatThrownBy(() -> service.criar(" ", req(p1))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- falha de integração ----------

    @Test
    void erroDeConsultaMantemPendenteAgendaBackoffEAuditaAteEsgotarTentativas() {
        when(central.registrar(any(), any())).thenReturn("CTR-E");
        when(central.consultar("CTR-E")).thenThrow(new CentralIntegracaoException(Tipo.TIMEOUT, "sem resposta"));
        Operacao op = service.criar("ke", req(p1));

        service.processarPendente(op.getId(), "CTR-E");
        Operacao apos1 = operacoes.findById(op.getId()).orElseThrow();
        assertThat(apos1.getStatus()).isEqualTo(StatusOperacao.PENDENTE);
        assertThat(apos1.getTentativasConsulta()).isEqualTo(1);
        assertThat(apos1.getProximaConsultaEm()).isAfter(Instant.now());
        assertThat(eventosDe(apos1)).contains("ERRO_CONSULTA_CENTRAL");

        service.processarPendente(op.getId(), "CTR-E");
        service.processarPendente(op.getId(), "CTR-E");
        Operacao fim = operacoes.findById(op.getId()).orElseThrow();
        assertThat(fim.getStatus()).isEqualTo(StatusOperacao.FALHA_INTEGRACAO);
        assertThat(statusParcela(p1)).isEqualTo(StatusParcela.ABERTA);
    }

    @Test
    void respostaPendenteRespeitaRetryAfterEnaoMudaEstado() {
        when(central.registrar(any(), any())).thenReturn("CTR-P");
        when(central.consultar("CTR-P")).thenReturn(new CentralGateway.ConsultaCentral(StatusOperacao.PENDENTE, null, 60));
        Operacao op = service.criar("kp", req(p1));
        service.processarPendente(op.getId(), "CTR-P");
        Operacao apos = operacoes.findById(op.getId()).orElseThrow();
        assertThat(apos.getStatus()).isEqualTo(StatusOperacao.PENDENTE);
        assertThat(apos.getProximaConsultaEm()).isAfter(Instant.now().plusSeconds(50));
    }

    // ---------- transições de estado ----------

    @Test
    void confirmacaoPortabilizaParcelasEAtualizaContaDeRecebimento() {
        when(central.registrar(any(), any())).thenReturn("CTR-A");
        when(central.consultar("CTR-A")).thenReturn(new CentralGateway.ConsultaCentral(StatusOperacao.CONFIRMADA, null, null));
        Operacao op = service.criar("ka", req(p1, p2));
        service.processarPendente(op.getId(), "CTR-A");

        Operacao conf = operacoes.findById(op.getId()).orElseThrow();
        assertThat(conf.getStatus()).isEqualTo(StatusOperacao.CONFIRMADA);
        assertThat(statusParcela(p1)).isEqualTo(StatusParcela.PORTABILIZADA);
        assertThat(statusParcela(p2)).isEqualTo(StatusParcela.PORTABILIZADA);
        ContaBeneficiario conta = contas.findById(beneficiario).orElseThrow();
        assertThat(conta.getConta().getConta()).isEqualTo("99999-0");
        assertThat(conta.getOperacaoId()).isEqualTo(op.getId());
        assertThat(eventosDe(conf)).containsExactly("SOLICITADA", "PROTOCOLO_RECEBIDO", "CONFIRMADA", "CONTA_RECEBIMENTO_ATUALIZADA");
    }

    @Test
    void rejeicaoLiberaParcelasERegistraMotivo() {
        when(central.registrar(any(), any())).thenReturn("CTR-R");
        when(central.consultar("CTR-R")).thenReturn(new CentralGateway.ConsultaCentral(StatusOperacao.REJEITADA, "Margem insuficiente", null));
        Operacao op = service.criar("kr", req(p1));
        service.processarPendente(op.getId(), "CTR-R");

        Operacao rej = operacoes.findById(op.getId()).orElseThrow();
        assertThat(rej.getStatus()).isEqualTo(StatusOperacao.REJEITADA);
        assertThat(rej.getMotivoRejeicao()).isEqualTo("Margem insuficiente");
        assertThat(statusParcela(p1)).isEqualTo(StatusParcela.ABERTA);
        assertThat(contas.findById(beneficiario)).isEmpty();
        assertThat(eventosDe(rej)).containsExactly("SOLICITADA", "PROTOCOLO_RECEBIDO", "REJEITADA");
    }

    @Test
    void estadoTerminalNaoEhReprocessadoNemSobrescrito() {
        when(central.registrar(any(), any())).thenReturn("CTR-T");
        when(central.consultar("CTR-T")).thenReturn(new CentralGateway.ConsultaCentral(StatusOperacao.CONFIRMADA, null, null));
        Operacao op = service.criar("kt", req(p1));
        service.processarPendente(op.getId(), "CTR-T");
        int eventosAntes = eventosDe(op).size();

        when(central.consultar("CTR-T")).thenReturn(new CentralGateway.ConsultaCentral(StatusOperacao.REJEITADA, "x", null));
        service.processarPendente(op.getId(), "CTR-T");

        assertThat(operacoes.findById(op.getId()).orElseThrow().getStatus()).isEqualTo(StatusOperacao.CONFIRMADA);
        assertThat(eventosDe(op)).hasSize(eventosAntes);
    }

    @Test
    void transicoesPermitidasSaoApenasAPartirDePendente() {
        for (StatusOperacao destino : List.of(StatusOperacao.CONFIRMADA, StatusOperacao.REJEITADA, StatusOperacao.FALHA_INTEGRACAO))
            assertThat(StatusOperacao.PENDENTE.podeTransitarPara(destino)).isTrue();
        assertThat(StatusOperacao.PENDENTE.podeTransitarPara(StatusOperacao.PENDENTE)).isFalse();
        for (StatusOperacao terminal : List.of(StatusOperacao.CONFIRMADA, StatusOperacao.REJEITADA, StatusOperacao.FALHA_INTEGRACAO))
            for (StatusOperacao destino : StatusOperacao.values())
                assertThat(terminal.podeTransitarPara(destino)).isFalse();
    }

    @Test
    void operacaoTerminalLancaExcecaoAoTentarMudarDeEstado() {
        Operacao op = new Operacao("k", "h", "b", "c", new ContaRecebimento("1", "2", "3"));
        op.atualizar(StatusOperacao.CONFIRMADA, null);
        assertThatThrownBy(() -> op.atualizar(StatusOperacao.REJEITADA, "x")).isInstanceOf(IllegalStateException.class);
    }
}
