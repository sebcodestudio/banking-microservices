package com.banco.yankiservice.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica solicitudes hacia account-service en el topico
 * {@code yanki.account.requests} (Fase 12). No hay ninguna llamada REST
 * de yanki-service hacia otro microservicio: esta es la unica via de
 * integracion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YankiEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRequest(YankiAccountRequestEvent event) {
        kafkaTemplate.send(YankiAccountResponseListener.REQUEST_TOPIC, event.walletId(), event);
        log.info("Solicitud Yanki publicada: correlationId={}, walletId={}, operationType={}",
                event.correlationId(), event.walletId(), event.operationType());
    }
}
