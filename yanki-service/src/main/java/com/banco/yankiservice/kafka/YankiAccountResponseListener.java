package com.banco.yankiservice.kafka;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.banco.yankiservice.model.YankiOperation;
import com.banco.yankiservice.model.YankiOperationStatus;
import com.banco.yankiservice.model.YankiTransaction;
import com.banco.yankiservice.model.YankiTransactionType;
import com.banco.yankiservice.model.YankiWallet;
import com.banco.yankiservice.repository.YankiOperationRepository;
import com.banco.yankiservice.repository.YankiTransactionRepository;
import com.banco.yankiservice.repository.YankiWalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Consume las respuestas de account-service (Fase 12, topico
 * {@code yanki.account.responses}) y actualiza el {@link YankiWallet} y
 * el {@link YankiOperation} correspondientes: los endpoints REST que
 * inician estas operaciones ya respondieron {@code 202 Accepted}, asi que
 * el resultado solo queda disponible aqui, consultable via
 * {@code GET /api/yanki/operations/{correlationId}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YankiAccountResponseListener {

    public static final String REQUEST_TOPIC = "yanki.account.requests";
    public static final String RESPONSE_TOPIC = "yanki.account.responses";
    private static final String CONSUMER_GROUP = "yanki-service";

    private final YankiOperationRepository operationRepository;
    private final YankiWalletRepository walletRepository;
    private final YankiTransactionRepository transactionRepository;

    @KafkaListener(topics = RESPONSE_TOPIC, groupId = CONSUMER_GROUP)
    public void onResponse(YankiAccountResponseEvent event) {
        log.info("Respuesta Yanki recibida: correlationId={}, success={}", event.correlationId(), event.success());
        operationRepository.findByCorrelationId(event.correlationId())
                .flatMap(operation -> apply(operation, event))
                .doOnError(error -> log.warn("Error aplicando respuesta Yanki correlationId={}: {}",
                        event.correlationId(), error.getMessage()))
                .block();
    }

    private Mono<Void> apply(YankiOperation operation, YankiAccountResponseEvent event) {
        if (!event.success()) {
            operation.setStatus(YankiOperationStatus.FAILED);
            operation.setErrorMessage(event.errorMessage());
            return operationRepository.save(operation).then();
        }
        return switch (event.operationType()) {
            case LINK_CARD -> walletRepository.findById(operation.getWalletId())
                    .flatMap(wallet -> {
                        wallet.setLinkedDebitCardId(operation.getDebitCardId());
                        wallet.setLinkedAccountId(event.accountId());
                        return walletRepository.save(wallet);
                    })
                    .then(markCompleted(operation));
            // CREDIT acredita el monedero (carga); DEBIT lo debita (retiro) - ver YankiOperationType.
            case CREDIT -> adjustBalance(operation, operation.getAmount(), YankiTransactionType.LOAD)
                    .then(markCompleted(operation));
            case DEBIT -> adjustBalance(operation, operation.getAmount().negate(), YankiTransactionType.WITHDRAW)
                    .then(markCompleted(operation));
        };
    }

    private Mono<YankiTransaction> adjustBalance(YankiOperation operation, BigDecimal delta, YankiTransactionType transactionType) {
        return walletRepository.findById(operation.getWalletId())
                .flatMap(wallet -> {
                    wallet.setBalance(wallet.getBalance().add(delta));
                    return walletRepository.save(wallet);
                })
                .then(transactionRepository.save(YankiTransaction.builder()
                        .walletId(operation.getWalletId())
                        .type(transactionType)
                        .amount(operation.getAmount())
                        .movementDate(Instant.now())
                        .build()));
    }

    private Mono<Void> markCompleted(YankiOperation operation) {
        operation.setStatus(YankiOperationStatus.COMPLETED);
        return operationRepository.save(operation).then();
    }
}
