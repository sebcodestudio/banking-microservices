package com.banco.creditservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.banco.creditservice.client.CustomerClient;
import com.banco.creditservice.client.CustomerType;
import com.banco.creditservice.model.CreditCard;
import com.banco.creditservice.model.CreditMovement;
import com.banco.creditservice.model.CreditMovementType;
import com.banco.creditservice.model.CreditProduct;
import com.banco.creditservice.model.CreditStatus;
import com.banco.creditservice.repository.CreditCardRepository;
import com.banco.creditservice.repository.CreditMovementRepository;
import com.banco.creditservice.repository.CreditProductRepository;
import com.banco.creditservice.service.strategy.CreditGrantStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementacion de las reglas de negocio de productos de credito.
 *
 * <p>Las reglas propias de cada subtipo de credito (personal,
 * empresarial, tarjeta) estan delegadas a una {@link CreditGrantStrategy}
 * por tipo (patron Strategy), en vez de resolverse aqui con una cadena
 * de {@code instanceof}. Spring inyecta automaticamente todas las
 * estrategias registradas como beans; este servicio solo elige la que
 * corresponde al tipo concreto de cada credito.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private final CreditProductRepository creditProductRepository;
    private final CreditCardRepository creditCardRepository;
    private final CreditMovementRepository creditMovementRepository;
    private final CustomerClient customerClient;
    private final List<CreditGrantStrategy> grantStrategies;

    @Override
    public Mono<CreditProduct> create(CreditProduct credit) {
        return customerClient.findCustomerById(credit.getCustomerId())
                .flatMap(customer -> validateAndPrepare(credit, customer.customerType()))
                .flatMap(creditProductRepository::save)
                .doOnNext(saved -> log.info("Credito otorgado: id={}, type={}, customerId={}",
                        saved.getId(), saved.getClass().getSimpleName(), saved.getCustomerId()))
                .doOnError(error -> log.warn("Otorgamiento de credito rechazado para customerId={}: {}",
                        credit.getCustomerId(), error.getMessage()));
    }

    /**
     * Completa los campos comunes, rechaza el otorgamiento si el cliente
     * tiene deuda vencida (Fase 10) y delega el resto de la validacion a
     * la estrategia del tipo de credito.
     */
    private Mono<CreditProduct> validateAndPrepare(CreditProduct credit, CustomerType customerType) {
        credit.setId(null);
        if (credit.getOpeningDate() == null) {
            credit.setOpeningDate(LocalDate.now());
        }
        if (credit.getNextPaymentDueDate() == null) {
            credit.setNextPaymentDueDate(LocalDate.now().plusMonths(1));
        }
        return hasOverdueDebt(credit.getCustomerId())
                .flatMap(overdue -> overdue
                        ? Mono.<CreditProduct>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El cliente tiene deuda vencida y no puede adquirir un nuevo producto de credito"))
                        : resolveStrategy(credit).prepareAndValidateGrant(credit, customerType));
    }

    @Override
    public Flux<CreditProduct> findAll() {
        return creditProductRepository.findAll();
    }

    @Override
    public Mono<CreditProduct> findById(String id) {
        return creditProductRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Credit not found with id " + id)));
    }

    @Override
    public Mono<CreditProduct> update(String id, CreditProduct credit) {
        return findById(id)
                .flatMap(existing -> {
                    if (!existing.getClass().equals(credit.getClass())) {
                        return Mono.<CreditProduct>error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "No se puede cambiar el tipo de un credito existente"));
                    }
                    credit.setId(existing.getId());
                    credit.setCreditNumber(existing.getCreditNumber());
                    credit.setCustomerId(existing.getCustomerId());
                    credit.setCreatedAt(existing.getCreatedAt());
                    return creditProductRepository.save(credit);
                });
    }

    @Override
    public Mono<Void> delete(String id) {
        return findById(id).flatMap(creditProductRepository::delete);
    }

    @Override
    public Mono<CreditMovement> pay(String creditId, BigDecimal amount, String payerCustomerId) {
        return validateAmount(amount)
                .then(validatePayerExists(payerCustomerId))
                .then(findById(creditId))
                .flatMap(credit -> resolveStrategy(credit).validateAndApplyPayment(credit, amount))
                .flatMap(credit -> applyPayment(credit, amount, payerCustomerId))
                .doOnError(error -> log.warn("Pago rechazado para creditId={}: {}", creditId, error.getMessage()));
    }

    /** Si se indica un pagador, valida contra customer-service que exista (Fase 10: pago de credito de terceros). */
    private Mono<Void> validatePayerExists(String payerCustomerId) {
        if (payerCustomerId == null || payerCustomerId.isBlank()) {
            return Mono.empty();
        }
        return customerClient.findCustomerById(payerCustomerId).then();
    }

    @Override
    public Mono<CreditMovement> consume(String creditId, BigDecimal amount) {
        return validateAmount(amount)
                .then(findById(creditId))
                .flatMap(credit -> resolveStrategy(credit).validateAndApplyConsumption(credit, amount))
                .flatMap(credit -> applyConsumption(credit, amount))
                .doOnError(error -> log.warn("Consumo rechazado para creditId={}: {}", creditId, error.getMessage()));
    }

    @Override
    public Flux<CreditMovement> getMovements(String creditId) {
        return findById(creditId)
                .thenMany(creditMovementRepository.findByCreditIdOrderByMovementDateDesc(creditId));
    }

    @Override
    public Mono<Boolean> hasActiveCreditCard(String customerId) {
        return creditCardRepository.existsByCustomerIdAndStatus(customerId, CreditStatus.ACTIVE);
    }

    @Override
    public Mono<Boolean> hasOverdueDebt(String customerId) {
        return creditProductRepository.existsByCustomerIdAndBalanceGreaterThanAndNextPaymentDueDateBefore(
                customerId, BigDecimal.ZERO, LocalDate.now());
    }

    @Override
    public Flux<CreditMovement> getMovementsReport(String creditId, Instant from, Instant to) {
        return findById(creditId)
                .thenMany(creditMovementRepository.findByCreditIdAndMovementDateBetweenOrderByMovementDateDesc(
                        creditId, from, to));
    }

    @Override
    public Flux<CreditMovement> getLastTenCardMovements(String creditId) {
        return findById(creditId)
                .flatMapMany(credit -> credit instanceof CreditCard
                        ? creditMovementRepository.findByCreditIdOrderByMovementDateDesc(creditId).take(10)
                        : Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Este reporte solo aplica a tarjetas de credito")));
    }

    /** Busca, entre las estrategias registradas, la que sabe manejar el tipo concreto del credito. */
    private CreditGrantStrategy resolveStrategy(CreditProduct credit) {
        return grantStrategies.stream()
                .filter(strategy -> strategy.supports(credit))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No hay una estrategia de reglas de negocio registrada para "
                                + credit.getClass().getSimpleName()));
    }

    private Mono<Void> validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El monto debe ser mayor a cero"));
        }
        return Mono.empty();
    }

    /**
     * Persiste un pago y su movimiento. Ademas de aplicar el pago (ya
     * reflejado en {@code credit.balance} por la estrategia), avanza
     * {@code nextPaymentDueDate} un mes si queda balance pendiente, o lo
     * limpia si el credito quedo saldado (Fase 10). Registra
     * {@code payerCustomerId} (el titular si no se especifico otro).
     */
    private Mono<CreditMovement> applyPayment(CreditProduct credit, BigDecimal amount, String payerCustomerId) {
        credit.setNextPaymentDueDate(credit.getBalance().signum() == 0 ? null : LocalDate.now().plusMonths(1));
        String effectivePayer = (payerCustomerId == null || payerCustomerId.isBlank())
                ? credit.getCustomerId() : payerCustomerId;
        Instant now = Instant.now();
        return creditProductRepository.save(credit)
                .then(creditMovementRepository.save(CreditMovement.builder()
                        .creditId(credit.getId())
                        .movementType(CreditMovementType.PAYMENT)
                        .amount(amount)
                        .balanceAfter(credit.getBalance())
                        .movementDate(now)
                        .payerCustomerId(effectivePayer)
                        .build()))
                .doOnNext(movement -> log.info(
                        "Pago registrado: creditId={}, amount={}, balanceAfter={}, payerCustomerId={}",
                        credit.getId(), amount, credit.getBalance(), effectivePayer));
    }

    private Mono<CreditMovement> applyConsumption(CreditProduct credit, BigDecimal amount) {
        Instant now = Instant.now();
        return creditProductRepository.save(credit)
                .then(creditMovementRepository.save(CreditMovement.builder()
                        .creditId(credit.getId())
                        .movementType(CreditMovementType.CONSUMPTION)
                        .amount(amount)
                        .balanceAfter(credit.getBalance())
                        .movementDate(now)
                        .build()))
                .doOnNext(movement -> log.info("Consumo registrado: creditId={}, amount={}, balanceAfter={}",
                        credit.getId(), amount, credit.getBalance()));
    }
}
