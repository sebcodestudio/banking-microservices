package com.banco.accountservice.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

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
import com.banco.accountservice.model.CheckingAccount;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.repository.BankAccountRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link CheckingAccountRuleStrategy}, incluyendo
 * las reglas del perfil PYME agregadas en la Fase 8.
 */
@ExtendWith(MockitoExtension.class)
class CheckingAccountRuleStrategyTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private CreditClient creditClient;

    private CheckingAccountRuleStrategy strategy;

    private CheckingAccount checkingAccount() {
        return CheckingAccount.builder()
                .customerId("customer-1")
                .balance(BigDecimal.ZERO)
                .currency("PEN")
                .maintenanceFee(new BigDecimal("15.00"))
                .freeMonthlyTransactionLimit(10)
                .transactionFeeAmount(BigDecimal.ZERO)
                .build();
    }

    private CustomerDto personalCustomer() {
        return new CustomerDto("customer-1", CustomerType.PERSONAL, "45678912", CustomerProfile.STANDARD);
    }

    private CustomerDto standardBusinessCustomer() {
        return new CustomerDto("customer-1", CustomerType.BUSINESS, "20123456789", CustomerProfile.STANDARD);
    }

    private CustomerDto pymeCustomer() {
        return new CustomerDto("customer-1", CustomerType.BUSINESS, "20123456789", CustomerProfile.PYME);
    }

    @BeforeEach
    void setUp() {
        strategy = new CheckingAccountRuleStrategy(bankAccountRepository, creditClient);
    }

    @Test
    void supportsSoloReconoceCuentasCorrientes() {
        assertThat(strategy.supports(checkingAccount())).isTrue();
        assertThat(strategy.supports(SavingsAccount.builder().build())).isFalse();
    }

    @Test
    void limpiaFirmantesAutorizadosYValidaUnicidadParaClientePersonal() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.empty());
        CheckingAccount account = checkingAccount();

        StepVerifier.create(strategy.prepareAndValidateOpening(account, personalCustomer()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(account.getAuthorizedSigners()).isEmpty();
    }

    @Test
    void rechazaSegundaCuentaCorrienteParaElMismoClientePersonal() {
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.just(checkingAccount()));

        StepVerifier.create(strategy.prepareAndValidateOpening(checkingAccount(), personalCustomer()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409)
                .verify();
    }

    @Test
    void permiteAperturaCuandoElClienteYaTieneUnaCuentaDeAhorroDeOtroTipo() {
        SavingsAccount otraCuenta = SavingsAccount.builder()
                .customerId("customer-1").currency("PEN")
                .monthlyMovementLimit(5).freeMonthlyTransactionLimit(3)
                .transactionFeeAmount(BigDecimal.ZERO).build();
        when(bankAccountRepository.findByHoldersContaining("customer-1")).thenReturn(Flux.just(otraCuenta));

        StepVerifier.create(strategy.prepareAndValidateOpening(checkingAccount(), personalCustomer()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void permiteMultiplesCuentasCorrientesParaClienteEmpresarialEstandar() {
        StepVerifier.create(strategy.prepareAndValidateOpening(checkingAccount(), standardBusinessCustomer()))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void rechazaAperturaPymeSinTarjetaDeCreditoPrevia() {
        when(creditClient.hasActiveCreditCard("customer-1")).thenReturn(Mono.just(false));

        StepVerifier.create(strategy.prepareAndValidateOpening(checkingAccount(), pymeCustomer()))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("tarjeta de credito"))
                .verify();
    }

    @Test
    void aperturaPymeForzaComisionDeMantenimientoAceroYExigeTarjetaPrevia() {
        when(creditClient.hasActiveCreditCard("customer-1")).thenReturn(Mono.just(true));
        CheckingAccount account = checkingAccount();

        StepVerifier.create(strategy.prepareAndValidateOpening(account, pymeCustomer()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(account.getMaintenanceFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noTieneLimiteDeMovimientos() {
        StepVerifier.create(strategy.validateMovementAllowed(checkingAccount()))
                .verifyComplete();
    }
}
