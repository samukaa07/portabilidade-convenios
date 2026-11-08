package br.com.desafio.portabilidade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "central.polling-enabled", havingValue = "true", matchIfMissing = true)
public class PollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);

    private final PortabilidadeService service;

    public PollingScheduler(PortabilidadeService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${central.polling-delay-ms:1000}")
    public void consultarCentral() {
        try {
            service.atualizarPendentes();
        } catch (RuntimeException e) {
            log.error("Falha ao atualizar operações pendentes", e);
        }
    }
}
