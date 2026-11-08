package br.com.desafio.portabilidade.service;

import br.com.desafio.portabilidade.domain.StatusOperacao;
import br.com.desafio.portabilidade.service.CentralIntegracaoException.Tipo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;

/**
 * Cliente HTTP da Central: timeouts, tradução de erros, verificação de assinatura das respostas e retry no registro.
 */
@Component
public class CentralHttpClient implements CentralGateway {

    static final String HEADER_ASSINATURA = "X-Assinatura";

    private final RestClient rest;
    private final AssinaturaService assinatura;
    private final BackoffPolicy backoff;
    private final ObjectMapper mapper;
    private final int maxTentativasRegistro;

    public CentralHttpClient(AssinaturaService assinatura, BackoffPolicy backoff, ObjectMapper mapper,
                             @Value("${central.base-url:http://localhost:${server.port:8080}/central}") String baseUrl,
                             @Value("${central.connect-timeout-ms:1000}") int connectTimeoutMs,
                             @Value("${central.read-timeout-ms:2000}") int readTimeoutMs,
                             @Value("${central.register-max-attempts:3}") int maxTentativasRegistro) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.assinatura = assinatura;
        this.backoff = backoff;
        this.mapper = mapper;
        this.maxTentativasRegistro = maxTentativasRegistro;
    }

    @Override
    public String registrar(String idempotencyKey, SolicitacaoCentral solicitacao) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return executar(() -> registrarUmaVez(idempotencyKey, solicitacao));
            } catch (CentralIntegracaoException e) {
                if (!e.isRetentavel() || tentativa >= maxTentativasRegistro) throw e;
                dormir(backoff.proximo(tentativa).toMillis());
            }
        }
    }

    private String registrarUmaVez(String key, SolicitacaoCentral s) {
        ResponseEntity<String> r = rest.post().uri("/operacoes-portabilidade")
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).body(s)
                .retrieve().toEntity(String.class);
        if (r.getStatusCode().value() != 202)
            throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Esperado 202, recebido " + r.getStatusCode().value());
        String protocolo = ler(r.getBody()).path("protocolo").asText(null);
        if (protocolo == null || protocolo.isBlank())
            throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Resposta sem protocolo");
        verificar(r, protocolo);
        return protocolo;
    }

    @Override
    public ConsultaCentral consultar(String protocolo) {
        return executar(() -> {
            ResponseEntity<String> r = rest.get().uri("/operacoes-portabilidade/{p}", protocolo).retrieve().toEntity(String.class);
            int code = r.getStatusCode().value();
            if (code == 204) return new ConsultaCentral(StatusOperacao.PENDENTE, null, retryAfter(r));
            if (code != 200)
                throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Status HTTP inesperado: " + code);
            JsonNode json = ler(r.getBody());
            StatusOperacao status;
            try {
                status = StatusOperacao.valueOf(json.path("status").asText(""));
            } catch (IllegalArgumentException e) {
                throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Status desconhecido: " + json.path("status").asText());
            }
            if (status == StatusOperacao.FALHA_INTEGRACAO)
                throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Status inválido retornado pela Central");
            String motivo = json.path("motivoRejeicao").isNull() ? null : json.path("motivoRejeicao").asText(null);
            verificar(r, protocolo + "|" + status + "|" + (motivo == null ? "" : motivo));
            return new ConsultaCentral(status, motivo, retryAfter(r));
        });
    }

    private <T> T executar(Supplier<T> chamada) {
        try {
            return chamada.get();
        } catch (CentralIntegracaoException e) {
            throw e;
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException)
                throw new CentralIntegracaoException(Tipo.TIMEOUT, "Central não respondeu no tempo limite", e);
            throw new CentralIntegracaoException(Tipo.INDISPONIVEL, "Central inacessível: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code >= 500)
                throw new CentralIntegracaoException(Tipo.INDISPONIVEL, "Central indisponível (HTTP " + code + ")", e);
            throw new CentralIntegracaoException(Tipo.REQUISICAO_RECUSADA, "Central recusou a requisição (HTTP " + code + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Resposta inesperada da Central: " + e.getMessage(), e);
        }
    }

    private void verificar(ResponseEntity<String> r, String conteudoAssinado) {
        String recebida = r.getHeaders().getFirst(HEADER_ASSINATURA);
        if (!assinatura.validar(conteudoAssinado, recebida))
            throw new CentralIntegracaoException(Tipo.ASSINATURA_INVALIDA, "Assinatura da resposta da Central ausente ou inválida");
    }

    private JsonNode ler(String body) {
        try {
            return mapper.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw new CentralIntegracaoException(Tipo.RESPOSTA_INESPERADA, "Corpo da resposta não é JSON válido", e);
        }
    }

    private Integer retryAfter(ResponseEntity<String> r) {
        String v = r.getHeaders().getFirst("Retry-After");
        try {
            return v == null ? null : Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
