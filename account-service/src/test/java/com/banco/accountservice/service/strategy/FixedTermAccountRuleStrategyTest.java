package com.banco.accountservice.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.client.CustomerProfile;
import com.banco.accountservice.client.CustomerType;
import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.FixedTermAccount;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.repository.AccountMovementRepository;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link FixedTermAccountRuleStrategy}.
 */
@ExtendWith(MockitoExtension.class)
class FixedTermAccountRuleStrategyTest {

    @Mock
    private AccountMovementRepository accountMovementRepository;

    private FixedTermAccountRuleStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FixedTermAccountRuleStrategy(accountMovementRepository);
    }

    private FixedTermAccount fixedTermAccount(int specificMovementDay) {
        return FixedTermAccount.builder()
                .customerId("customer-1")
                .balance(BigDecimal.ZERO)
                .currency("PEN")
                .specificMovementDay(specificMovementDay)
                .termMonths(12)
                .build();
    }

    @Test
    void supportsSoloReconoceCuentasAPlazoFijo() {
        assertThat(strategy.supports(fixedTermAccount(1))).isTrue();
        assertThat(strategy.supports(SavingsAccount.builder().build())).isFalse();
    }

    @Test
    void rechazaAperturaParaClienteEmpresarial() {
        CustomerDto business = new CustomerDto("customer-1", CustomerType.BUSINESS, "20123456789", CustomerProfile.STANDARD);
        StepVerifier.create(strategy.prepareAndValidateOpening(fixedTermAccount(1), business))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void rechazaMovimientoEnUnDiaDistintoAlEspecifico() {
        int diaDistinto = LocalDate.now().getDayOfMonth() == 1 ? 2 : 1;
        FixedTermAccount account = fixedTermAccount(diaDistinto);
        account.setId("acc-1");

        StepVerifier.create(strategy.validateMovementAllowed(account))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("dia"))
                .verify();
    }

    @Test
    void rechazaSegundoMovimientoEnElMismoPeriodoAunEnElDiaCorrecto() {
        int hoy = LocalDate.now().getDayOfMonth();
        FixedTermAccount account = fixedTermAccount(hoy);
        account.setId("acc-1");

        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                "acc-1", org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Flux.just(AccountMovement.builder().build()));

        StepVerifier.create(strategy.validateMovementAllowed(account))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("unico movimiento"))
                .verify();
    }

    @Test
    void permiteElUnicoMovimientoDelPeriodoEnElDiaCorrecto() {
        int hoy = LocalDate.now().getDayOfMonth();
        FixedTermAccount account = fixedTermAccount(hoy);
        account.setId("acc-1");

        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                "acc-1", org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(strategy.validateMovementAllowed(account))
                .verifyComplete();
    }
}
