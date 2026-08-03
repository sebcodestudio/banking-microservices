package com.banco.yankiservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.banco.yankiservice.kafka.YankiAccountRequestEvent;
import com.banco.yankiservice.kafka.YankiEventPublisher;
import com.banco.yankiservice.model.YankiOperation;
import com.banco.yankiservice.model.YankiOperationType;
import com.banco.yankiservice.model.YankiTransaction;
import com.banco.yankiservice.model.YankiTransactionType;
import com.banco.yankiservice.model.YankiWallet;
import com.banco.yankiservice.repository.YankiOperationRepository;
import com.banco.yankiservice.repository.YankiTransactionRepository;
import com.banco.yankiservice.repository.YankiWalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementacion de las reglas de negocio del monedero Yanki (Fase 12).
 * Las operaciones que involucran a account-service ({@link #linkDebitCard},
 * {@link #load}, {@link #withdraw}) solo publican una solicitud a Kafka y
 * devuelven la {@link YankiOperation} en estado pendiente: el resultado
 * llega de forma asincrona (ver {@code YankiAccountResponseListener}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YankiWalletServiceImpl implements YankiWalletService {

    private final YankiWalletRepository walletRepository;
    private final YankiTransactionRepository transactionRepository;
    private final YankiOperationRepository operationRepository;
    private final YankiEventPublisher eventPublisher;

    @Override
    public Mono<YankiWallet> register(YankiWallet wallet) {
        wallet.setId(null);
        return validateUniqueness(wallet)
                .then(walletRepository.save(wallet))
                .doOnNext(saved -> log.info("Monedero Yanki registrado: id={}, phoneNumber={}",
                        saved.getId(), saved.getPhoneNumber()))
                .doOnError(error -> log.warn("Registro de monedero Yanki rechazado para phoneNumber={}: {}",
                        wallet.getPhoneNumber(), error.getMessage()));
    }

    private Mono<Void> validateUniqueness(YankiWallet wallet) {
        return walletRepository.existsByDocumentNumber(wallet.getDocumentNumber())
                .flatMap(exists -> exists
                        ? Mono.<Boolean>error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Ya existe un monedero con ese numero de documento"))
                        : walletRepository.existsByPhoneNumber(wallet.getPhoneNumber()))
                .flatMap(exists -> exists
                        ? Mono.<Boolean>error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Ya existe un monedero con ese numero de celular"))
                        : Mono.just(Boolean.FALSE))
                .then();
    }

    @Override
    public Mono<YankiWallet> findById(String id) {
        return walletRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Wallet not found with id " + id)));
    }

    private Mono<YankiWallet> findByPhone(String phoneNumber) {
        return walletRepository.findByPhoneNumber(phoneNumber)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe un monedero con el numero de celular " + phoneNumber)));
    }

    @Override
    public Mono<YankiTransferResult> transfer(String senderPhone, String receiverPhone, BigDecimal amount) {
        if (senderPhone.equals(receiverPhone)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monedero emisor y el receptor no pueden ser el mismo"));
        }
        return validateAmount(amount)
                .then(Mono.zip(findByPhone(senderPhone), findByPhone(receiverPhone)))
                .flatMap(pair -> executeTransfer(pair.getT1(), pair.getT2(), amount))
                .doOnError(error -> log.warn("Transferencia Yanki rechazada: senderPhone={}, receiverPhone={}: {}",
                        senderPhone, receiverPhone, error.getMessage()));
    }

    private Mono<YankiTransferResult> executeTransfer(YankiWallet sender, YankiWallet receiver, BigDecimal amount) {
        if (sender.getBalance().compareTo(amount) < 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente"));
        }
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        Instant now = Instant.now();

        return walletRepository.save(sender)
                .then(transactionRepository.save(YankiTransaction.builder()
                        .walletId(sender.getId())
                        .type(YankiTransactionType.SEND)
                        .amount(amount)
                        .counterpartyPhone(receiver.getPhoneNumber())
                        .movementDate(now)
                        .build()))
                .flatMap(sendTransaction -> walletRepository.save(receiver)
                        .then(transactionRepository.save(YankiTransaction.builder()
                                .walletId(receiver.getId())
                                .type(YankiTransactionType.RECEIVE)
                                .amount(amount)
                                .counterpartyPhone(sender.getPhoneNumber())
                                .movementDate(now)
                                .build()))
                        .map(receiveTransaction -> new YankiTransferResult(sendTransaction, receiveTransaction)));
    }

    @Override
    public Mono<YankiOperation> linkDebitCard(String walletId, String debitCardId) {
        return findById(walletId)
                .then(createOperation(walletId, YankiOperationType.LINK_CARD, debitCardId, null))
                .doOnNext(operation -> eventPublisher.publishRequest(new YankiAccountRequestEvent(
                        operation.getCorrelationId(), walletId, YankiOperationType.LINK_CARD, debitCardId, null, null)));
    }

    @Override
    public Mono<YankiOperation> load(String walletId, BigDecimal amount) {
        return initiateAccountOperation(walletId, YankiOperationType.CREDIT, amount);
    }

    @Override
    public Mono<YankiOperation> withdraw(String walletId, BigDecimal amount) {
        return initiateAccountOperation(walletId, YankiOperationType.DEBIT, amount);
    }

    /** Valida el monedero y el saldo (para retiros), crea la operacion pendiente y publica la solicitud a Kafka. */
    private Mono<YankiOperation> initiateAccountOperation(String walletId, YankiOperationType type, BigDecimal amount) {
        return validateAmount(amount)
                .then(findById(walletId))
                .flatMap(wallet -> {
                    if (wallet.getLinkedAccountId() == null) {
                        return Mono.<YankiWallet>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El monedero no tiene una tarjeta de debito vinculada"));
                    }
                    if (type == YankiOperationType.DEBIT && wallet.getBalance().compareTo(amount) < 0) {
                        return Mono.<YankiWallet>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Saldo insuficiente en el monedero"));
                    }
                    return Mono.just(wallet);
                })
                .flatMap(wallet -> createOperation(walletId, type, null, amount)
                        .doOnNext(operation -> eventPublisher.publishRequest(new YankiAccountRequestEvent(
                                operation.getCorrelationId(), walletId, type, null, wallet.getLinkedAccountId(), amount))));
    }

    private Mono<YankiOperation> createOperation(String walletId, YankiOperationType type, String debitCardId, BigDecimal amount) {
        return operationRepository.save(YankiOperation.builder()
                .correlationId(UUID.randomUUID().toString())
                .walletId(walletId)
                .operationType(type)
                .debitCardId(debitCardId)
                .amount(amount)
                .build());
    }

    @Override
    public Mono<YankiOperation> getOperation(String correlationId) {
        return operationRepository.findByCorrelationId(correlationId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Operation not found with correlationId " + correlationId)));
    }

    @Override
    public Flux<YankiTransaction> getMovements(String walletId) {
        return findById(walletId)
                .thenMany(transactionRepository.findByWalletIdOrderByMovementDateDesc(walletId));
    }

    private Mono<Void> validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor a cero"));
        }
        return Mono.empty();
    }
}
