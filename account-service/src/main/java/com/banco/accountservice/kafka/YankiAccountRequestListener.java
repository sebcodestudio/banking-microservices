package com.banco.accountservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.banco.accountservice.model.DebitCard;
import com.banco.accountservice.model.DebitCardStatus;
import com.banco.accountservice.service.AccountService;
import com.banco.accountservice.service.DebitCardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Consume las solicitudes de yanki-service (Fase 12, topico
 * {@code yanki.account.requests}) y las resuelve reutilizando los mismos
 * metodos de negocio que exponen los endpoints REST
 * ({@link DebitCardService}, {@link AccountService}), sin exponer ningun
 * endpoint REST nuevo para esta integracion: toda la comunicacion entre
 * yanki-service y account-service es via Kafka, publicando el resultado
 * en {@code yanki.account.responses}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YankiAccountRequestListener {

    public static final String REQUEST_TOPIC = "yanki.account.requests";
    public static final String RESPONSE_TOPIC = "yanki.account.responses";
    private static final String CONSUMER_GROUP = "account-service";

    private final DebitCardService debitCardService;
    private final AccountService accountService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = REQUEST_TOPIC, groupId = CONSUMER_GROUP)
    public void onRequest(YankiAccountRequestEvent event) {
        log.info("Solicitud Yanki recibida: correlationId={}, walletId={}, operationType={}",
                event.correlationId(), event.walletId(), event.operationType());
        YankiAccountResponseEvent response = resolve(event)
                .onErrorResume(error -> Mono.just(failure(event, error)))
                .block();
        kafkaTemplate.send(RESPONSE_TOPIC, response.walletId(), response);
        log.info("Respuesta Yanki publicada: correlationId={}, success={}", response.correlationId(), response.success());
    }

    /**
     * {@code CREDIT}/{@code DEBIT} describen el efecto sobre el
     * <b>monedero</b> Yanki (ver {@link YankiOperationType}), no sobre la
     * cuenta: acreditar el monedero (carga) implica debitar la cuenta
     * vinculada, y debitar el monedero (retiro) implica acreditarla.
     */
    private Mono<YankiAccountResponseEvent> resolve(YankiAccountRequestEvent event) {
        return switch (event.operationType()) {
            case LINK_CARD -> linkCard(event);
            case CREDIT -> moveFunds(event, false);
            case DEBIT -> moveFunds(event, true);
        };
    }

    private Mono<YankiAccountResponseEvent> linkCard(YankiAccountRequestEvent event) {
        return debitCardService.findById(event.debitCardId())
                .map(card -> validateCardActive(card)
                        ? success(event, card.getAccountId(), null)
                        : failure(event, new IllegalStateException("La tarjeta de debito no esta activa")));
    }

    private boolean validateCardActive(DebitCard card) {
        return card.getStatus() == DebitCardStatus.ACTIVE;
    }

    /** {@code creditAccount=true} deposita en la cuenta; {@code false} la debita via tarjeta de debito. */
    private Mono<YankiAccountResponseEvent> moveFunds(YankiAccountRequestEvent event, boolean creditAccount) {
        Mono<?> movement = creditAccount
                ? accountService.deposit(event.accountId(), event.amount())
                : accountService.payWithDebitCard(event.accountId(), event.amount());
        return movement
                .then(accountService.findById(event.accountId()))
                .map(account -> success(event, event.accountId(), account.getBalance()));
    }

    private YankiAccountResponseEvent success(YankiAccountRequestEvent event, String accountId, java.math.BigDecimal newAccountBalance) {
        return new YankiAccountResponseEvent(event.correlationId(), event.walletId(), event.operationType(),
                true, accountId, newAccountBalance, null);
    }

    private YankiAccountResponseEvent failure(YankiAccountRequestEvent event, Throwable error) {
        return new YankiAccountResponseEvent(event.correlationId(), event.walletId(), event.operationType(),
                false, null, null, error.getMessage());
    }
}
