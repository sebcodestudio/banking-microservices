package com.banco.creditservice.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.CreditCard;
import com.banco.creditservice.model.PersonalCredit;

import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link CreditCardGrantStrategy}: es la unica
 * estrategia que admite consumos, validados contra el limite disponible.
 */
class CreditCardGrantStrategyTest {

    private CreditCardGrantStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CreditCardGrantStrategy();
    }

    private CreditCard creditCard() {
        return CreditCard.builder()
                .customerId("customer-1")
                .currency("PEN")
                .creditLimit(new BigDecimal("5000.00"))
                .balance(BigDecimal.ZERO)
                .build();
    }

    @Test
    void supportsSoloReconoceTarjetasDeCredito() {
        assertThat(strategy.supports(creditCard())).isTrue();
        assertThat(strategy.supports(PersonalCredit.builder().build())).isFalse();
    }

    @Test
    void seOtorgaConBalanceCeroSinImportarElTipoDeCliente() {
        CreditCard card = creditCard();

        StepVerifier.create(strategy.prepareAndValidateGrant(card, CustomerType.BUSINESS))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(card.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void permiteUnConsumoDentroDelLimiteDisponible() {
        CreditCard card = creditCard();

        StepVerifier.create(strategy.validateAndApplyConsumption(card, new BigDecimal("1200.00")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(card.getBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(card.getAvailableLimit()).isEqualByComparingTo(new BigDecimal("3800.00"));
    }

    @Test
    void rechazaUnConsumoQueExcedeElLimiteDisponible() {
        CreditCard card = creditCard();
        card.setBalance(new BigDecimal("4800.00"));

        StepVerifier.create(strategy.validateAndApplyConsumption(card, new BigDecimal("500.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void unPagoReduceElConsumoAcumuladoSinMarcarLaTarjetaComoPagada() {
        CreditCard card = creditCard();
        card.setBalance(new BigDecimal("1000.00"));

        StepVerifier.create(strategy.validateAndApplyPayment(card, new BigDecimal("300.00")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(card.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }
}
