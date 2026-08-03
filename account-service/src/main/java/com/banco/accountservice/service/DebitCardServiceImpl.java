package com.banco.accountservice.service;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.DebitCard;
import com.banco.accountservice.model.MovementType;
import com.banco.accountservice.repository.AccountMovementRepository;
import com.banco.accountservice.repository.BankAccountRepository;
import com.banco.accountservice.repository.DebitCardRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementacion de las reglas de negocio de tarjetas de debito (Fase 11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebitCardServiceImpl implements DebitCardService {

    private final DebitCardRepository debitCardRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountMovementRepository accountMovementRepository;
    private final AccountService accountService;

    @Override
    public Mono<DebitCard> issue(DebitCard debitCard) {
        debitCard.setId(null);
        return bankAccountRepository.findById(debitCard.getAccountId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Account not found with id " + debitCard.getAccountId())))
                .flatMap(account -> validateOwnership(account, debitCard))
                .then(debitCardRepository.existsByAccountId(debitCard.getAccountId()))
                .flatMap(exists -> exists
                        ? Mono.<DebitCard>error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "La cuenta ya tiene una tarjeta de debito"))
                        : debitCardRepository.save(debitCard))
                .doOnNext(saved -> log.info("Tarjeta de debito emitida: id={}, accountId={}, customerId={}",
                        saved.getId(), saved.getAccountId(), saved.getCustomerId()))
                .doOnError(error -> log.warn("Emision de tarjeta de debito rechazada para accountId={}: {}",
                        debitCard.getAccountId(), error.getMessage()));
    }

    private Mono<Void> validateOwnership(BankAccount account, DebitCard debitCard) {
        if (!account.getCustomerId().equals(debitCard.getCustomerId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta indicada no pertenece al cliente customerId=" + debitCard.getCustomerId()));
        }
        return Mono.empty();
    }

    @Override
    public Flux<DebitCard> findAll() {
        return debitCardRepository.findAll();
    }

    @Override
    public Mono<DebitCard> findById(String id) {
        return debitCardRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Debit card not found with id " + id)));
    }

    @Override
    public Mono<AccountMovement> pay(String debitCardId, BigDecimal amount) {
        return findById(debitCardId)
                .flatMap(card -> accountService.payWithDebitCard(card.getAccountId(), amount))
                .doOnError(error -> log.warn("Pago con tarjeta de debito rechazado para debitCardId={}: {}",
                        debitCardId, error.getMessage()));
    }

    @Override
    public Flux<AccountMovement> getMovements(String debitCardId) {
        return findById(debitCardId)
                .flatMapMany(card -> accountMovementRepository.findByAccountIdAndMovementTypeOrderByMovementDateDesc(
                        card.getAccountId(), MovementType.DEBIT_CARD_PAYMENT));
    }
}
