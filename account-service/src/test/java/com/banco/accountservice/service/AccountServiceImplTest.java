package com.banco.accountservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CreditClient;
import com.banco.accountservice.client.CustomerClient;
import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.client.CustomerProfile;
import com.banco.accountservice.client.CustomerType;
import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.MovementType;
import com.banco.accountservice.model.SavingsAccount;
import com.banco.accountservice.repository.AccountMovementRepository;
import com.banco.accountservice.repository.BankAccountRepository;
import com.banco.accountservice.service.strategy.AccountRuleStrategy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link AccountServiceImpl}. Las reglas propias de
 * cada tipo de cuenta se mockean a traves de {@link AccountRuleStrategy}
 * (ya probadas de forma aislada en su propio paquete de tests); aqui se
 * verifica la orquestacion: seleccion de estrategia, persistencia,
 * monto minimo de apertura y comision por exceso de transacciones
 * (Fase 8).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private AccountMovementRepository accountMovementRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private CreditClient creditClient;

    @Mock
    private AccountRuleStrategy ruleStrategy;

    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        lenient().when(ruleStrategy.supports(any(BankAccount.class))).thenReturn(true);
        lenient().when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Mono.just(false));
        accountService = new AccountServiceImpl(
                bankAccountRepository, accountMovementRepository, customerClient, creditClient, List.of(ruleStrategy));
    }

    private SavingsAccount savingsAccount() {
        return SavingsAccount.builder()
                .customerId("customer-1")
                .balance(new BigDecimal("100.00"))
                .currency("PEN")
                .monthlyMovementLimit(5)
                .minimumOpeningAmount(BigDecimal.ZERO)
                .freeMonthlyTransactionLimit(3)
                .transactionFeeAmount(new BigDecimal("5.00"))
                .build();
    }

    private CustomerDto personalCustomer(CustomerType type) {
        return new CustomerDto("customer-1", type, "45678912", CustomerProfile.STANDARD);
    }

    /** Simula que no hay movimientos previos este mes (para no disparar la comision salvo que el test la busque). */
    private void stubSinMovimientosPreviosEsteMes() {
        lenient().when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                        "acc-1", any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.empty());
    }

    @Test
    void createValidaContraCustomerServiceYDelegaEnLaEstrategia() {
        SavingsAccount account = savingsAccount();
        SavingsAccount saved = savingsAccount();
        saved.setId("acc-1");

        CustomerDto customer = personalCustomer(CustomerType.PERSONAL);
        when(customerClient.findCustomerById("customer-1")).thenReturn(Mono.just(customer));
        when(ruleStrategy.prepareAndValidateOpening(account, customer)).thenReturn(Mono.just(account));
        when(bankAccountRepository.save(account)).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.create(account))
                .expectNextMatches(a -> a.getId().equals("acc-1"))
                .verifyComplete();
    }

    @Test
    void createRechazaCuandoElBalanceInicialNoCumpleElMontoMinimoDeApertura() {
        SavingsAccount account = savingsAccount();
        account.setBalance(new BigDecimal("10.00"));
        account.setMinimumOpeningAmount(new BigDecimal("50.00"));

        when(customerClient.findCustomerById("customer-1"))
                .thenReturn(Mono.just(personalCustomer(CustomerType.PERSONAL)));

        StepVerifier.create(accountService.create(account))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("monto de apertura"))
                .verify();
    }

    @Test
    void createPermiteMontoDeAperturaCero() {
        SavingsAccount account = savingsAccount();
        account.setBalance(BigDecimal.ZERO);
        account.setMinimumOpeningAmount(BigDecimal.ZERO);
        SavingsAccount saved = savingsAccount();
        saved.setId("acc-1");
        saved.setBalance(BigDecimal.ZERO);

        CustomerDto customer = personalCustomer(CustomerType.PERSONAL);
        when(customerClient.findCustomerById("customer-1")).thenReturn(Mono.just(customer));
        when(ruleStrategy.prepareAndValidateOpening(account, customer)).thenReturn(Mono.just(account));
        when(bankAccountRepository.save(account)).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.create(account))
                .expectNextMatches(a -> a.getId().equals("acc-1"))
                .verifyComplete();
    }

    @Test
    void createPropagaElRechazoDeLaEstrategia() {
        SavingsAccount account = savingsAccount();
        CustomerDto customer = personalCustomer(CustomerType.BUSINESS);

        when(customerClient.findCustomerById("customer-1")).thenReturn(Mono.just(customer));
        when(ruleStrategy.prepareAndValidateOpening(account, customer)).thenReturn(
                Mono.error(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "rechazado")));

        StepVerifier.create(accountService.create(account))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void depositValidaElMontoYRegistraElMovimientoSinComisionDentroDelLimiteGratuito() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        stubSinMovimientosPreviosEsteMes();

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account));
        when(ruleStrategy.validateMovementAllowed(account)).thenReturn(Mono.empty());
        when(bankAccountRepository.save(account)).thenReturn(Mono.just(account));
        when(accountMovementRepository.save(any(AccountMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(accountService.deposit("acc-1", new BigDecimal("50.00")))
                .expectNextMatches(m -> m.getMovementType() == MovementType.DEPOSIT
                        && m.getBalanceAfter().compareTo(new BigDecimal("150.00")) == 0)
                .verifyComplete();

        verify(accountMovementRepository, times(1)).save(any(AccountMovement.class));
    }

    @Test
    void depositCobraComisionAlSuperarElLimiteDeTransaccionesGratuitas() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        // Ya hay 3 movimientos este mes (igual al limite gratuito) -> la 4a transaccion se cobra.
        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                        "acc-1", any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.just(
                        AccountMovement.builder().movementType(MovementType.DEPOSIT).build(),
                        AccountMovement.builder().movementType(MovementType.WITHDRAWAL).build(),
                        AccountMovement.builder().movementType(MovementType.DEPOSIT).build()));

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account));
        when(ruleStrategy.validateMovementAllowed(account)).thenReturn(Mono.empty());
        when(bankAccountRepository.save(account)).thenReturn(Mono.just(account));
        when(accountMovementRepository.save(any(AccountMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(accountService.deposit("acc-1", new BigDecimal("50.00")))
                .expectNextMatches(m -> m.getMovementType() == MovementType.DEPOSIT)
                .verifyComplete();

        // Se registran 2 movimientos: el deposito y la comision (FEE).
        verify(accountMovementRepository, times(2)).save(any(AccountMovement.class));
        // Balance final: 100 + 50 (deposito) - 5 (comision) = 145.
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("145.00"));
    }

    @Test
    void depositNoCobraComisionCuandoLaCuentaTieneComisionCero() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        account.setTransactionFeeAmount(BigDecimal.ZERO);
        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                        "acc-1", any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.just(
                        AccountMovement.builder().movementType(MovementType.DEPOSIT).build(),
                        AccountMovement.builder().movementType(MovementType.DEPOSIT).build(),
                        AccountMovement.builder().movementType(MovementType.DEPOSIT).build()));

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account));
        when(ruleStrategy.validateMovementAllowed(account)).thenReturn(Mono.empty());
        when(bankAccountRepository.save(account)).thenReturn(Mono.just(account));
        when(accountMovementRepository.save(any(AccountMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(accountService.deposit("acc-1", new BigDecimal("50.00")))
                .expectNextMatches(m -> m.getMovementType() == MovementType.DEPOSIT)
                .verifyComplete();

        verify(accountMovementRepository, times(1)).save(any(AccountMovement.class));
    }

    @Test
    void depositRechazaMontoNegativoOCero() {
        StepVerifier.create(accountService.deposit("acc-1", BigDecimal.ZERO))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    void withdrawRechazaFondosInsuficientes() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        account.setBalance(new BigDecimal("10.00"));

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account));

        StepVerifier.create(accountService.withdraw("acc-1", new BigDecimal("50.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("Fondos insuficientes"))
                .verify();
    }

    @Test
    void withdrawDescuentaElMontoCuandoHayFondosSuficientes() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        account.setBalance(new BigDecimal("100.00"));
        stubSinMovimientosPreviosEsteMes();

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account));
        when(ruleStrategy.validateMovementAllowed(account)).thenReturn(Mono.empty());
        when(bankAccountRepository.save(account)).thenReturn(Mono.just(account));
        when(accountMovementRepository.save(any(AccountMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(accountService.withdraw("acc-1", new BigDecimal("30.00")))
                .expectNextMatches(m -> m.getMovementType() == MovementType.WITHDRAWAL
                        && m.getBalanceAfter().compareTo(new BigDecimal("70.00")) == 0)
                .verifyComplete();

        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void createRechazaCuandoElClienteTieneDeudaVencida() {
        SavingsAccount account = savingsAccount();
        CustomerDto customer = personalCustomer(CustomerType.PERSONAL);

        when(customerClient.findCustomerById("customer-1")).thenReturn(Mono.just(customer));
        when(creditClient.hasOverdueDebt("customer-1")).thenReturn(Mono.just(true));

        StepVerifier.create(accountService.create(account))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("deuda vencida"))
                .verify();
    }

    @Test
    void findByIdPropagaNotFoundCuandoNoExiste() {
        when(bankAccountRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(accountService.findById("no-existe"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void transferRegistraElRetiroEnOrigenYElDepositoEnDestino() {
        SavingsAccount source = savingsAccount();
        source.setId("acc-1");
        source.setBalance(new BigDecimal("100.00"));
        SavingsAccount destination = savingsAccount();
        destination.setId("acc-2");
        destination.setCustomerId("customer-2");
        destination.setBalance(new BigDecimal("50.00"));

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Mono.just(destination));
        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                        eq("acc-1"), any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.empty());
        when(accountMovementRepository.findByAccountIdAndMovementDateBetween(
                        eq("acc-2"), any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.empty());
        when(ruleStrategy.validateMovementAllowed(source)).thenReturn(Mono.empty());
        when(ruleStrategy.validateMovementAllowed(destination)).thenReturn(Mono.empty());
        when(bankAccountRepository.save(source)).thenReturn(Mono.just(source));
        when(bankAccountRepository.save(destination)).thenReturn(Mono.just(destination));
        when(accountMovementRepository.save(any(AccountMovement.class))).thenAnswer(
                invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(accountService.transfer("acc-1", "acc-2", new BigDecimal("30.00")))
                .expectNextMatches(result -> result.sourceMovement().getMovementType() == MovementType.TRANSFER_OUT
                        && result.sourceMovement().getBalanceAfter().compareTo(new BigDecimal("70.00")) == 0
                        && result.destinationMovement().getMovementType() == MovementType.TRANSFER_IN
                        && result.destinationMovement().getBalanceAfter().compareTo(new BigDecimal("80.00")) == 0)
                .verifyComplete();

        assertThat(source.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(destination.getBalance()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    void transferRechazaCuandoOrigenYDestinoSonLaMismaCuenta() {
        StepVerifier.create(accountService.transfer("acc-1", "acc-1", new BigDecimal("10.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("no pueden ser la misma"))
                .verify();
    }

    @Test
    void transferRechazaFondosInsuficientesEnLaCuentaOrigen() {
        SavingsAccount source = savingsAccount();
        source.setId("acc-1");
        source.setBalance(new BigDecimal("10.00"));
        SavingsAccount destination = savingsAccount();
        destination.setId("acc-2");
        destination.setCustomerId("customer-2");

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Mono.just(destination));

        StepVerifier.create(accountService.transfer("acc-1", "acc-2", new BigDecimal("50.00")))
                .expectErrorMatches(error -> error instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 400
                        && rse.getReason().contains("Fondos insuficientes"))
                .verify();
    }

    @Test
    void getMovementsReportDevuelveLosMovimientosDelRangoDeFechasSolicitado() {
        SavingsAccount account = savingsAccount();
        account.setId("acc-1");
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        AccountMovement movement = AccountMovement.builder().accountId("acc-1").movementType(MovementType.DEPOSIT).build();

        when(bankAccountRepository.findById("acc-1")).thenReturn(Mono.just(account));
        when(accountMovementRepository.findByAccountIdAndMovementDateBetweenOrderByMovementDateDesc("acc-1", from, to))
                .thenReturn(Flux.just(movement));

        StepVerifier.create(accountService.getMovementsReport("acc-1", from, to))
                .expectNext(movement)
                .verifyComplete();
    }
}
