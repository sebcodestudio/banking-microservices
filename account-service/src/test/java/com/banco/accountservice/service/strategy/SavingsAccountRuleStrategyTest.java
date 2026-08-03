package com.banco.accountservice.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CreditClient;
import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.client.CustomerProfile;
import com.banco.accountservice.client.CustomerType;
import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.CheckingAccount;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.repository.AccountMovementRepository;
import com.banco.accountservice.repository.BankAccountRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link SavingsAccountRuleStrategy}, incluyendo
 * las reglas del perfil VIP agregadas en la Fase 8.
 */
@ExtendWith(MockitoExtension.class)
class SavingsAccountRuleStrategyTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private AccountMovementRepository accountMovementRepository;

    @Mock
    private CreditClient creditClient;

    private SavingsAccountRuleStrategy strategy;

    private SavingsAccount savingsAccount() {
        return SavingsAccount.builder()
                .customerId("customer-1")
                .balance(BigDecimal.ZERO)
                .currency("PEN")
                .monthlyMovementLimit(5)
                .freeMonthlyTransactionLimit(3)
                .transactionFeeAmount(BigDecimal.ZERO)
                .build();
    }

    private CustomerDto standardCustomer() {
        return new CustomerDto("customer-1", CustomerType.PERSONAL, "45678912", CustomerProfile.STANDARD);
    }

    private CustomerDto vipCustomer() {
        return new CustomerDto("customer-1", CustomerType.PERSONAL, "45678912", CustomerProfile.VIP);
    }

    private CustomerDto businessCustomer() {
        return new CustomerDto("customer-1", CustomerType.BUSINESS, "20123456789", CustomerProfile.STANDARD);
    }

    @BeforeEach
    void setUp() {
        strategy = new SavingsAccountRuleStrategy(bankAccountRepository, accountMovementRepository, creditClient);
    }

    @Test
    void supportsSoloReconoceCuentasDeAhorro() {
        assertThat(strategy.supports(savingsAccount())).isTrue();
        assertThat(strategy.supports(CheckingAccount.builder().build())).isFalse();
    }

    @Test
    void rechazaAperturaParaClienteEmpresarial() {
        StepVerifier.create(strategy.prepareAndValidateOpening(savingsAccount(), businessCustomer()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void rechazaSegundaCuentaDeAhorroParaElMismoCliente() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.just(savingsAccount()));

        StepVerifier.create(strategy.prepareAndValidateOpening(savingsAccount(), standardCustomer()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409)
                .verify();
    }

    @Test
    void permiteAperturaEstandarCuandoElClientePersonalNoTieneOtraCuentaDeAhorro() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.empty());
        SavingsAccount account = savingsAccount();

        StepVerifier.create(strategy.prepareAndValidateOpening(account, standardCustomer()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(account.getMinimumDailyAverageBalance()).isNull();
    }

    @Test
    void permiteAperturaCuandoElClienteYaTieneUnaCuentaCorrienteDeOtroTipo() {
        CheckingAccount otraCuenta = CheckingAccount.builder()
                .customerId("customer-1").currency("PEN")
                .maintenanceFee(BigDecimal.ZERO).freeMonthlyTransactionLimit(10)
                .transactionFeeAmount(BigDecimal.ZERO).build();
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.just(otraCuenta));

        StepVerifier.create(strategy.prepareAndValidateOpening(savingsAccount(), standardCustomer()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void rechazaAperturaVipSinMontoMinimoDePromedioDiario() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.empty());
        SavingsAccount account = savingsAccount();

        StepVerifier.create(strategy.prepareAndValidateOpening(account, vipCustomer()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("promedio diario"))
                .verify();
    }

    @Test
    void rechazaAperturaVipSinTarjetaDeCreditoPrevia() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.empty());
        when(creditClient.hasActiveCreditCard("customer-1")).thenReturn(Mono.just(false));
        SavingsAccount account = savingsAccount();
        account.setMinimumDailyAverageBalance(new BigDecimal("1000.00"));

        StepVerifier.create(strategy.prepareAndValidateOpening(account, vipCustomer()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("tarjeta de credito"))
                .verify();
    }

    @Test
    void permiteAperturaVipConPromedioDiarioYTarjetaDeCreditoPrevia() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.empty());
        when(creditClient.hasActiveCreditCard("customer-1")).thenReturn(Mono.just(true));
        SavingsAccount account = savingsAccount();
        account.setMinimumDailyAverageBalance(new BigDecimal("1000.00"));

        StepVerifier.create(strategy.prepareAndValidateOpening(account, vipCustomer()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void rechazaMovimientoCuandoSeAlcanzoElLimiteMensual() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                "acc-1", org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Flux.just(
                        AccountMovement.builder().build(),
                        AccountMovement.builder().build(),
                        AccountMovement.builder().build(),
                        AccountMovement.builder().build(),
                        AccountMovement.builder().build()));

        StepVerifier.create(strategy.validateMovementAllowed(account))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void permiteMovimientoCuandoAunNoSeAlcanzaElLimite() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                "acc-1", org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Flux.just(AccountMovement.builder().build(), AccountMovement.builder().build()));

        StepVerifier.create(strategy.validateMovementAllowed(account))
                .verifyComplete();
    }
}
