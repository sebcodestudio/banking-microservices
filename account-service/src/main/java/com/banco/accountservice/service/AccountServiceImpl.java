package com.banco.accountservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.banco.accountservice.client.CreditClient;
import com.banco.accountservice.client.CustomerClient;
import com.banco.accountservice.client.CustomerDto;
import com.banco.accountservice.controller.TransferResult;
import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.MovementType;
import com.banco.accountservice.repository.AccountMovementRepository;
import com.banco.accountservice.repository.BankAccountRepository;
import com.banco.accountservice.service.strategy.AccountRuleStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementacion de las reglas de negocio de cuentas bancarias.
 *
 * <p>Las reglas propias de cada subtipo de cuenta (ahorro, corriente,
 * plazo fijo) estan delegadas a una {@link AccountRuleStrategy} por
 * tipo (patron Strategy), en vez de resolverse aqui con una cadena de
 * {@code instanceof}. Spring inyecta automaticamente todas las
 * estrategias registradas como beans; este servicio solo elige la que
 * corresponde al tipo concreto de cada cuenta.</p>
 *
 * <p>Fase 8: ademas de las reglas por tipo, este servicio aplica dos
 * reglas comunes a toda cuenta: (1) el monto de apertura no puede ser
 * menor al minimo configurado ({@code minimumOpeningAmount}, que puede
 * ser cero); y (2) las transacciones (depositos/retiros) que excedan el
 * limite mensual sin comision ({@code freeMonthlyTransactionLimit})
 * generan una comision adicional ({@code transactionFeeAmount}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final BankAccountRepository bankAccountRepository;
    private final AccountMovementRepository accountMovementRepository;
    private final CustomerClient customerClient;
    private final CreditClient creditClient;
    private final List<AccountRuleStrategy> ruleStrategies;

    @Override
    public Mono<BankAccount> create(BankAccount account) {
        return customerClient.findCustomerById(account.getCustomerId())
                .flatMap(customer -> validateAndPrepare(account, customer))
                .flatMap(bankAccountRepository::save)
                .doOnNext(saved -> log.info("Cuenta abierta: id={}, type={}, customerId={}",
                        saved.getId(), saved.getClass().getSimpleName(), saved.getCustomerId()))
                .doOnError(error -> log.warn("Apertura de cuenta rechazada para customerId={}: {}",
                        account.getCustomerId(), error.getMessage()));
    }

    /**
     * Completa los campos comunes, valida el monto minimo de apertura,
     * rechaza la apertura si el cliente tiene deuda vencida (Fase 10) y
     * delega el resto a la estrategia del tipo de cuenta.
     */
    private Mono<BankAccount> validateAndPrepare(BankAccount account, CustomerDto customer) {
        account.setId(null);
        if (account.getOpeningDate() == null) {
            account.setOpeningDate(LocalDate.now());
        }
        if (account.getHolders() == null || account.getHolders().isEmpty()) {
            account.setHolders(List.of(account.getCustomerId()));
        }
        if (account.getBalance().compareTo(account.getMinimumOpeningAmount()) < 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto de apertura (" + account.getBalance()
                            + ") no cumple el minimo requerido (" + account.getMinimumOpeningAmount() + ")"));
        }
        return creditClient.hasOverdueDebt(account.getCustomerId())
                .flatMap(overdue -> overdue
                        ? Mono.<BankAccount>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El cliente tiene deuda vencida y no puede adquirir un nuevo producto"))
                        : resolveStrategy(account).prepareAndValidateOpening(account, customer));
    }

    @Override
    public Flux<BankAccount> findAll() {
        return bankAccountRepository.findAll();
    }

    @Override
    public Mono<BankAccount> findById(String id) {
        return bankAccountRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Account not found with id " + id)));
    }

    @Override
    public Mono<BankAccount> update(String id, BankAccount account) {
        return findById(id)
                .flatMap(existing -> {
                    if (!existing.getClass().equals(account.getClass())) {
                        return Mono.<BankAccount>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "No se puede cambiar el tipo de una cuenta existente"));
                    }
                    account.setId(existing.getId());
                    account.setAccountNumber(existing.getAccountNumber());
                    account.setCustomerId(existing.getCustomerId());
                    account.setCreatedAt(existing.getCreatedAt());
                    return bankAccountRepository.save(account);
                });
    }

    @Override
    public Mono<Void> delete(String id) {
        return findById(id).flatMap(bankAccountRepository::delete);
    }

    @Override
    public Mono<AccountMovement> deposit(String accountId, BigDecimal amount) {
        return validateAmount(amount)
                .then(findById(accountId))
                .flatMap(account -> resolveStrategy(account).validateMovementAllowed(account).thenReturn(account))
                .flatMap(account -> applyMovementWithPossibleFee(account, MovementType.DEPOSIT, amount));
    }

    @Override
    public Mono<AccountMovement> withdraw(String accountId, BigDecimal amount) {
        return debit(accountId, amount, MovementType.WITHDRAWAL);
    }

    @Override
    public Mono<AccountMovement> payWithDebitCard(String accountId, BigDecimal amount) {
        return debit(accountId, amount, MovementType.DEBIT_CARD_PAYMENT);
    }

    /**
     * Logica comun a un retiro y a un pago con tarjeta de debito (Fase 11):
     * valida fondos y las reglas del tipo de cuenta, y aplica el
     * movimiento con el tipo indicado (incluye la comision por exceso de
     * transacciones si corresponde).
     */
    private Mono<AccountMovement> debit(String accountId, BigDecimal amount, MovementType type) {
        return validateAmount(amount)
                .then(findById(accountId))
                .flatMap(account -> {
                    if (account.getBalance().compareTo(amount) < 0) {
                        return Mono.<BankAccount>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Fondos insuficientes"));
                    }
                    return resolveStrategy(account).validateMovementAllowed(account).thenReturn(account);
                })
                .flatMap(account -> applyMovementWithPossibleFee(account, type, amount));
    }

    @Override
    public Flux<AccountMovement> getMovements(String accountId) {
        return findById(accountId)
                .thenMany(accountMovementRepository.findByAccountIdOrderByMovementDateDesc(accountId));
    }

    @Override
    public Mono<TransferResult> transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount) {
        if (sourceAccountId.equals(destinationAccountId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta de origen y la de destino no pueden ser la misma"));
        }
        return validateAmount(amount)
                .then(Mono.zip(findById(sourceAccountId), findById(destinationAccountId)))
                .flatMap(accounts -> executeTransfer(accounts.getT1(), accounts.getT2(), amount))
                .doOnNext(result -> log.info("Transferencia realizada: sourceAccountId={}, destinationAccountId={}, amount={}",
                        sourceAccountId, destinationAccountId, amount))
                .doOnError(error -> log.warn("Transferencia rechazada: sourceAccountId={}, destinationAccountId={}: {}",
                        sourceAccountId, destinationAccountId, error.getMessage()));
    }

    /** Valida fondos y las reglas de movimiento de ambas cuentas, y aplica el retiro en origen seguido del deposito en destino. */
    private Mono<TransferResult> executeTransfer(BankAccount source, BankAccount destination, BigDecimal amount) {
        if (source.getBalance().compareTo(amount) < 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fondos insuficientes"));
        }
        return resolveStrategy(source).validateMovementAllowed(source)
                .then(resolveStrategy(destination).validateMovementAllowed(destination))
                .then(applyMovementWithPossibleFee(source, MovementType.TRANSFER_OUT, amount))
                .flatMap(sourceMovement -> applyMovementWithPossibleFee(destination, MovementType.TRANSFER_IN, amount)
                        .map(destinationMovement -> new TransferResult(sourceMovement, destinationMovement)));
    }

    @Override
    public Flux<AccountMovement> getMovementsReport(String accountId, Instant from, Instant to) {
        return findById(accountId)
                .thenMany(accountMovementRepository.findByAccountIdAndMovementDateBetweenOrderByMovementDateDesc(
                        accountId, from, to));
    }

    /** Busca, entre las estrategias registradas, la que sabe manejar el tipo concreto de la cuenta. */
    private AccountRuleStrategy resolveStrategy(BankAccount account) {
        return ruleStrategies.stream()
                .filter(strategy -> strategy.supports(account))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No hay una estrategia de reglas de negocio registrada para "
                                + account.getClass().getSimpleName()));
    }

    private Mono<Void> validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto debe ser mayor a cero"));
        }
        return Mono.empty();
    }

    /**
     * Aplica el movimiento (deposito o retiro) y, si con el se supera el
     * numero de transacciones gratuitas del mes, cobra ademas la
     * comision configurada en la cuenta (Fase 8). El movimiento
     * devuelto al llamador es siempre el principal (deposito/retiro);
     * la comision, si aplica, se registra como un movimiento adicional
     * de tipo {@link MovementType#FEE}.
     */
    private Mono<AccountMovement> applyMovementWithPossibleFee(BankAccount account, MovementType type, BigDecimal amount) {
        Instant[] monthRange = currentMonthRange();
        return accountMovementRepository.findByAccountIdAndMovementDateBetween(account.getId(), monthRange[0], monthRange[1])
                .filter(movement -> movement.getMovementType() != MovementType.FEE)
                .count()
                .flatMap(billableTransactionsThisMonth -> {
                    boolean exceedsFreeLimit = billableTransactionsThisMonth >= account.getFreeMonthlyTransactionLimit();
                    boolean feeApplies = exceedsFreeLimit && account.getTransactionFeeAmount().signum() > 0;
                    return applyMovement(account, type, amount)
                            .flatMap(movement -> feeApplies
                                    ? chargeTransactionFee(account).thenReturn(movement)
                                    : Mono.just(movement));
                });
    }

    private Mono<AccountMovement> applyMovement(BankAccount account, MovementType type, BigDecimal amount) {
        BigDecimal newBalance = isCredit(type)
                ? account.getBalance().add(amount)
                : account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        Instant now = Instant.now();

        return bankAccountRepository.save(account)
                .then(accountMovementRepository.save(AccountMovement.builder()
                        .accountId(account.getId())
                        .movementType(type)
                        .amount(amount)
                        .balanceAfter(newBalance)
                        .movementDate(now)
                        .build()))
                .doOnNext(movement -> log.info("Movimiento registrado: accountId={}, type={}, amount={}, balanceAfter={}",
                        account.getId(), type, amount, newBalance));
    }

    private Mono<AccountMovement> chargeTransactionFee(BankAccount account) {
        BigDecimal fee = account.getTransactionFeeAmount();
        BigDecimal newBalance = account.getBalance().subtract(fee);
        account.setBalance(newBalance);
        Instant now = Instant.now();

        return bankAccountRepository.save(account)
                .then(accountMovementRepository.save(AccountMovement.builder()
                        .accountId(account.getId())
                        .movementType(MovementType.FEE)
                        .amount(fee)
                        .balanceAfter(newBalance)
                        .movementDate(now)
                        .build()))
                .doOnNext(feeMovement -> log.info(
                        "Comision por exceso de transacciones cobrada: accountId={}, amount={}, balanceAfter={}",
                        account.getId(), fee, newBalance));
    }

    /** Indica si el tipo de movimiento suma al balance de la cuenta (deposito o entrada de transferencia). */
    private boolean isCredit(MovementType type) {
        return type == MovementType.DEPOSIT || type == MovementType.TRANSFER_IN;
    }

    private Instant[] currentMonthRange() {
        LocalDate today = LocalDate.now();
        Instant start = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = today.withDayOfMonth(today.lengthOfMonth())
                .atTime(23, 59, 59)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return new Instant[] { start, end };
    }
}
