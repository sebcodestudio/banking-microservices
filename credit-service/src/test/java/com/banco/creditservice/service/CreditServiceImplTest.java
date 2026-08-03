package com.banco.creditservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerClient;
import com.banco.creditservice.client.CustomerDto;
import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.CreditCard;
import com.banco.creditservice.model.CreditMovement;
import com.banco.creditservice.model.CreditMovementType;
import com.banco.creditservice.model.CreditProduct;
import com.banco.creditservice.model.PersonalCredit;
import com.banco.creditservice.repository.CreditMovementRepository;
import com.banco.creditservice.repository.CreditProductRepository;
import com.banco.creditservice.service.strategy.CreditGrantStrategy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link CreditServiceImpl}. Las reglas propias de
 * cada tipo de credito se mockean a traves de {@link CreditGrantStrategy}
 * (ya probadas de forma aislada en su propio paquete de tests); aqui se
 * verifica la orquestacion: seleccion de estrategia, persistencia y
 * generacion de movimientos.
 */
@ExtendWith(MockitoExtension.class)
class CreditServiceImplTest {

    @Mock
    private CreditProductRepository creditProductRepository;

    @Mock
    private com.banco.creditservice.repository.CreditCardRepository creditCardRepository;

    @Mock
    private CreditMovementRepository creditMovementRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private CreditGrantStrategy grantStrategy;

    private CreditServiceImpl creditService;

    @BeforeEach
    void setUp() {
        lenient().when(grantStrategy.supports(any(CreditProduct.class))).thenReturn(true);
        lenient().when(creditProductRepository.existsByCustomerIdAndBalanceGreaterThanAndNextPaymentDueDateBefore(
                        eq("customer-1"), any(BigDecimal.class), any(LocalDate.class)))
                .thenReturn(Mono.just(false));
        creditService = new CreditServiceImpl(
                creditProductRepository, creditCardRepository, creditMovementRepository, customerClient, List.of(grantStrategy));
    }

    private PersonalCredit personalCredit() {
        return PersonalCredit.builder()
                .customerId("customer-1")
                .currency("PEN")
                .principalAmount(new BigDecimal("10000.00"))
                .interestRate(new BigDecimal("0.15"))
                .termMonths(24)
                .monthlyPayment(new BigDecimal("480.00"))
                .balance(new BigDecimal("480.00"))
                .build();
    }

    @Test
    void createValidaContraCustomerServiceYDelegaEnLaEstrategia() {
        PersonalCredit credit = personalCredit();
        PersonalCredit saved = personalCredit();
        saved.setId("credit-1");

        when(customerClient.findCustomerById("customer-1"))
                .thenReturn(Mono.just(new CustomerDto("customer-1", CustomerType.PERSONAL, "45678912")));
        when(grantStrategy.prepareAndValidateGrant(credit, CustomerType.PERSONAL)).thenReturn(Mono.just(credit));
        when(creditProductRepository.save(credit)).thenReturn(Mono.just(saved));

        StepVerifier.create(creditService.create(credit))
                .expectNextMatches(c -> c.getId().equals("credit-1"))
                .verifyComplete();
    }

    @Test
    void payValidaMontoYDelegaEnLaEstrategiaAntesDePersistir() {
        PersonalCredit credit = personalCredit();
        credit.setId("credit-1");

        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(credit));
        when(grantStrategy.validateAndApplyPayment(credit, new BigDecimal("480.00"))).thenReturn(Mono.just(credit));
        when(creditProductRepository.save(credit)).thenReturn(Mono.just(credit));
        when(creditMovementRepository.save(any(CreditMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(creditService.pay("credit-1", new BigDecimal("480.00"), null))
                .expectNextMatches(m -> m.getMovementType() == CreditMovementType.PAYMENT
                        && m.getPayerCustomerId().equals("customer-1"))
                .verifyComplete();
    }

    @Test
    void payRechazaMontoNegativoOCeroSinConsultarElRepositorio() {
        StepVerifier.create(creditService.pay("credit-1", BigDecimal.ZERO, null))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void payConPagadorDistintoValidaQueExistaYLoRegistraEnElMovimiento() {
        PersonalCredit credit = personalCredit();
        credit.setId("credit-1");
        credit.setBalance(BigDecimal.ZERO);

        when(customerClient.findCustomerById("customer-2"))
                .thenReturn(Mono.just(new CustomerDto("customer-2", CustomerType.PERSONAL, "11111111")));
        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(credit));
        when(grantStrategy.validateAndApplyPayment(credit, new BigDecimal("480.00"))).thenReturn(Mono.just(credit));
        when(creditProductRepository.save(credit)).thenReturn(Mono.just(credit));
        when(creditMovementRepository.save(any(CreditMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(creditService.pay("credit-1", new BigDecimal("480.00"), "customer-2"))
                .expectNextMatches(m -> m.getPayerCustomerId().equals("customer-2"))
                .verifyComplete();

        assertThat(credit.getNextPaymentDueDate()).isNull();
    }

    @Test
    void payRechazaCuandoElPagadorIndicadoNoExiste() {
        // findById no deberia alcanzarse (el chequeo del pagador corta antes),
        // pero se stubea igual: el argumento de .then(...) se evalua en Java
        // antes de subscribirse, y un mock sin stub de un metodo que retorna
        // Mono puede devolver null en vez de Mono.empty() segun la version
        // de Mockito resuelta (ver nota en CLAUDE.md).
        lenient().when(creditProductRepository.findById("credit-1")).thenReturn(Mono.empty());
        when(customerClient.findCustomerById("no-existe")).thenReturn(
                Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Customer not found with id no-existe")));

        StepVerifier.create(creditService.pay("credit-1", new BigDecimal("480.00"), "no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void hasOverdueDebtDelegaEnElRepositorio() {
        when(creditProductRepository.existsByCustomerIdAndBalanceGreaterThanAndNextPaymentDueDateBefore(
                        eq("customer-1"), any(BigDecimal.class), any(LocalDate.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(creditService.hasOverdueDebt("customer-1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void createRechazaCuandoElClienteTieneDeudaVencida() {
        PersonalCredit credit = personalCredit();

        when(customerClient.findCustomerById("customer-1"))
                .thenReturn(Mono.just(new CustomerDto("customer-1", CustomerType.PERSONAL, "45678912")));
        when(creditProductRepository.existsByCustomerIdAndBalanceGreaterThanAndNextPaymentDueDateBefore(
                        eq("customer-1"), any(BigDecimal.class), any(LocalDate.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(creditService.create(credit))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("deuda vencida"))
                .verify();
    }

    @Test
    void consumeDelegaEnLaEstrategiaYRegistraElMovimiento() {
        PersonalCredit credit = personalCredit();
        credit.setId("credit-1");

        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(credit));
        when(grantStrategy.validateAndApplyConsumption(credit, new BigDecimal("100.00"))).thenReturn(Mono.just(credit));
        when(creditProductRepository.save(credit)).thenReturn(Mono.just(credit));
        when(creditMovementRepository.save(any(CreditMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(creditService.consume("credit-1", new BigDecimal("100.00")))
                .expectNextMatches(m -> m.getMovementType() == CreditMovementType.CONSUMPTION)
                .verifyComplete();
    }

    @Test
    void consumePropagaElRechazoCuandoElTipoDeCreditoNoLoPermite() {
        PersonalCredit credit = personalCredit();
        credit.setId("credit-1");

        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(credit));
        when(grantStrategy.validateAndApplyConsumption(credit, new BigDecimal("100.00"))).thenReturn(
                Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Solo las tarjetas de credito admiten consumos")));

        StepVerifier.create(creditService.consume("credit-1", new BigDecimal("100.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void findByIdPropagaNotFoundCuandoNoExiste() {
        when(creditProductRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(creditService.findById("no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void hasActiveCreditCardDelegaEnElRepositorioDeTarjetas() {
        when(creditCardRepository.existsByCustomerIdAndStatus("customer-1", com.banco.creditservice.model.CreditStatus.ACTIVE))
                .thenReturn(Mono.just(true));

        StepVerifier.create(creditService.hasActiveCreditCard("customer-1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void getMovementsReportDevuelveLosMovimientosDelRangoDeFechasSolicitado() {
        PersonalCredit credit = personalCredit();
        credit.setId("credit-1");
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        CreditMovement movement = CreditMovement.builder().creditId("credit-1").movementType(CreditMovementType.PAYMENT).build();

        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(credit));
        when(creditMovementRepository.findByCreditIdAndMovementDateBetweenOrderByMovementDateDesc("credit-1", from, to))
                .thenReturn(Flux.just(movement));

        StepVerifier.create(creditService.getMovementsReport("credit-1", from, to))
                .expectNext(movement)
                .verifyComplete();
    }

    @Test
    void getLastTenCardMovementsDevuelveLosMovimientosDeUnaTarjetaDeCredito() {
        CreditCard card = CreditCard.builder()
                .customerId("customer-1")
                .currency("PEN")
                .creditLimit(new BigDecimal("2000.00"))
                .build();
        card.setId("credit-1");
        CreditMovement movement = CreditMovement.builder().creditId("credit-1").movementType(CreditMovementType.CONSUMPTION).build();

        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(card));
        when(creditMovementRepository.findByCreditIdOrderByMovementDateDesc("credit-1")).thenReturn(Flux.just(movement));

        StepVerifier.create(creditService.getLastTenCardMovements("credit-1"))
                .expectNext(movement)
                .verifyComplete();
    }

    @Test
    void getLastTenCardMovementsRechazaCuandoElCreditoNoEsTarjeta() {
        PersonalCredit credit = personalCredit();
        credit.setId("credit-1");

        when(creditProductRepository.findById("credit-1")).thenReturn(Mono.just(credit));

        StepVerifier.create(creditService.getLastTenCardMovements("credit-1"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("tarjetas de credito"))
                .verify();
    }
}
