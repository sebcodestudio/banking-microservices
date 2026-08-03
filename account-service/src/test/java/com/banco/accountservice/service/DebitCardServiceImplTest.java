package com.banco.accountservice.service;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.DebitCard;
import com.banco.accountservice.model.MovementType;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.repository.AccountMovementRepository;
import com.banco.accountservice.repository.BankAccountRepository;
import com.banco.accountservice.repository.DebitCardRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link DebitCardServiceImpl} (Fase 11).
 */
@ExtendWith(MockitoExtension.class)
class DebitCardServiceImplTest {

    @Mock
    private DebitCardRepository debitCardRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private AccountMovementRepository accountMovementRepository;

    @Mock
    private AccountService accountService;

    private DebitCardServiceImpl debitCardService;

    @BeforeEach
    void setUp() {
        debitCardService = new DebitCardServiceImpl(
                debitCardRepository, bankAccountRepository, accountMovementRepository, accountService);
    }

    private SavingsAccount account() {
        SavingsAccount account = SavingsAccount.builder()
                .customerId("customer-1")
                .balance(new BigDecimal("100.00"))
                .currency("PEN")
                .monthlyMovementLimit(5)
                .freeMonthlyTransactionLimit(3)
                .transactionFeeAmount(BigDecimal.ZERO)
                .build();
        account.setId("acc-1");
        return account;
    }

    private DebitCard debitCardRequest() {
        return DebitCard.builder()
                .cardNumber("4000-0001")
                .accountId("acc-1")
                .customerId("customer-1")
                .build();
    }

    @Test
    void issueCreaLaTarjetaCuandoLaCuentaExisteYNoTieneOtraTarjeta() {
        DebitCard request = debitCardRequest();
        DebitCard saved = debitCardRequest();
        saved.setId("card-1");

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account()));
        when(debitCardRepository.existsByAccountId("acc-1")).thenReturn(Mono.just(false));
        when(debitCardRepository.save(request)).thenReturn(Mono.just(saved));

        StepVerifier.create(debitCardService.issue(request))
                .expectNextMatches(card -> card.getId().equals("card-1"))
                .verifyComplete();
    }

    @Test
    void issueRechazaCuandoLaCuentaNoExiste() {
        // El argumento de .then(...) se evalua en Java antes de subscribirse
        // (ver nota en CLAUDE.md), asi que se stubea igual aunque no deberia alcanzarse.
        lenient().when(debitCardRepository.existsByAccountId("acc-1")).thenReturn(Mono.just(false));
        DebitCard request = debitCardRequest();
        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.issue(request))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void issueRechazaCuandoLaCuentaNoPerteneceAlCliente() {
        lenient().when(debitCardRepository.existsByAccountId("acc-1")).thenReturn(Mono.just(false));
        DebitCard request = debitCardRequest();
        request.setCustomerId("customer-2");
        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account()));

        StepVerifier.create(debitCardService.issue(request))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void issueRechazaCuandoLaCuentaYaTieneUnaTarjeta() {
        DebitCard request = debitCardRequest();
        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account()));
        when(debitCardRepository.existsByAccountId("acc-1")).thenReturn(Mono.just(true));

        StepVerifier.create(debitCardService.issue(request))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409)
                .verify();
    }

    @Test
    void findByIdPropagaNotFoundCuandoNoExiste() {
        when(debitCardRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(debitCardService.findById("no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void payDelegaEnAccountServiceConLaCuentaVinculada() {
        DebitCard card = debitCardRequest();
        card.setId("card-1");
        AccountMovement movement = AccountMovement.builder()
                .accountId("acc-1").movementType(MovementType.DEBIT_CARD_PAYMENT).build();

        when(debitCardRepository.findById("card-1")).thenReturn(Mono.just(card));
        when(accountService.payWithDebitCard("acc-1", new BigDecimal("30.00"))).thenReturn(Mono.just(movement));

        StepVerifier.create(debitCardService.pay("card-1", new BigDecimal("30.00")))
                .expectNext(movement)
                .verifyComplete();
    }

    @Test
    void getMovementsFiltraPorCuentaYTipoDebitCardPayment() {
        DebitCard card = debitCardRequest();
        card.setId("card-1");
        AccountMovement movement = AccountMovement.builder()
                .accountId("acc-1").movementType(MovementType.DEBIT_CARD_PAYMENT).build();

        when(debitCardRepository.findById("card-1")).thenReturn(Mono.just(card));
        when(accountMovementRepository.findByAccountIdAndMovementTypeOrderByMovementDateDesc(
                        "acc-1", MovementType.DEBIT_CARD_PAYMENT))
                .thenReturn(Flux.just(movement));

        StepVerifier.create(debitCardService.getMovements("card-1"))
                .expectNext(movement)
                .verifyComplete();
    }
}
