package com.banco.creditservice.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.BusinessCredit;
import com.banco.creditservice.model.CreditStatus;
import com.banco.creditservice.model.PersonalCredit;
import com.banco.creditservice.repository.PersonalCreditRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link PersonalCreditGrantStrategy}.
 */
@ExtendWith(MockitoExtension.class)
class PersonalCreditGrantStrategyTest {

    @Mock
    private PersonalCreditRepository personalCreditRepository;

    private PersonalCreditGrantStrategy strategy;

    private PersonalCredit personalCredit() {
        return PersonalCredit.builder()
                .customerId("customer-1")
                .currency("PEN")
                .principalAmount(new BigDecimal("10000.00"))
                .interestRate(new BigDecimal("0.15"))
                .termMonths(24)
                .monthlyPayment(new BigDecimal("480.00"))
                .build();
    }

    @BeforeEach
    void setUp() {
        strategy = new PersonalCreditGrantStrategy(personalCreditRepository);
    }

    @Test
    void supportsSoloReconoceCreditosPersonales() {
        assertThat(strategy.supports(personalCredit())).isTrue();
        assertThat(strategy.supports(BusinessCredit.builder().build())).isFalse();
    }

    @Test
    void rechazaOtorgamientoParaClienteEmpresarial() {
        StepVerifier.create(strategy.prepareAndValidateGrant(personalCredit(), CustomerType.BUSINESS))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void rechazaSegundoCreditoPersonalActivo() {
        when(personalCreditRepository.existsByCustomerIdAndStatus("customer-1", CreditStatus.ACTIVE))
                .thenReturn(Mono.just(true));

        StepVerifier.create(strategy.prepareAndValidateGrant(personalCredit(), CustomerType.PERSONAL))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 409)
                .verify();
    }

    @Test
    void inicializaElBalanceComoElMontoPrincipalYPermiteElOtorgamiento() {
        when(personalCreditRepository.existsByCustomerIdAndStatus("customer-1", CreditStatus.ACTIVE))
                .thenReturn(Mono.just(false));
        PersonalCredit credit = personalCredit();

        StepVerifier.create(strategy.prepareAndValidateGrant(credit, CustomerType.PERSONAL))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(credit.getBalance()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void unPagoQueCubreTodoElSaldoMarcaElCreditoComoPagado() {
        PersonalCredit credit = personalCredit();
        credit.setBalance(new BigDecimal("480.00"));

        StepVerifier.create(strategy.validateAndApplyPayment(credit, new BigDecimal("480.00")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(credit.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.PAID);
    }

    @Test
    void rechazaUnPagoQueExcedeElSaldoPendiente() {
        PersonalCredit credit = personalCredit();
        credit.setBalance(new BigDecimal("100.00"));

        StepVerifier.create(strategy.validateAndApplyPayment(credit, new BigDecimal("200.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }
}
