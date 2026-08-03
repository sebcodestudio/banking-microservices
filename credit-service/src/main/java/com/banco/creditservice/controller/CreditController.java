package com.banco.creditservice.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.banco.creditservice.model.CreditMovement;
import com.banco.creditservice.model.CreditProduct;
import com.banco.creditservice.service.CreditService;

import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST con las operaciones CRUD y de credito del recurso Credit.
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    /** Otorga un nuevo credito (personal, empresarial o tarjeta de credito). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreditProduct> create(@Valid @RequestBody CreditProduct credit) {
        return creditService.create(credit);
    }

    /** Obtiene todos los creditos registrados. */
    @GetMapping
    public Flux<CreditProduct> findAll() {
        return creditService.findAll();
    }

    /** Obtiene un credito por su identificador. */
    @GetMapping("/{id}")
    public Mono<CreditProduct> findById(@PathVariable String id) {
        return creditService.findById(id);
    }

    /** Actualiza los datos de un credito existente. */
    @PutMapping("/{id}")
    public Mono<CreditProduct> update(@PathVariable String id, @Valid @RequestBody CreditProduct credit) {
        return creditService.update(id, credit);
    }

    /** Elimina un credito por su identificador. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return creditService.delete(id);
    }

    /** Consulta el saldo (deuda o consumo) disponible de un credito. */
    @GetMapping("/{id}/balance")
    public Mono<CreditProduct> getBalance(@PathVariable String id) {
        return creditService.findById(id);
    }

    /**
     * Registra un pago hacia la deuda de un credito personal, empresarial
     * o de tarjeta. Admite {@code payerCustomerId} opcional (Fase 10) para
     * que un cliente pague el credito de un tercero.
     */
    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreditMovement> pay(@PathVariable String id, @Valid @RequestBody MovementRequest request) {
        return creditService.pay(id, request.amount(), request.payerCustomerId());
    }

    /** Registra un consumo sobre una tarjeta de credito. */
    @PostMapping("/{id}/consumptions")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreditMovement> consume(@PathVariable String id, @Valid @RequestBody MovementRequest request) {
        return creditService.consume(id, request.amount());
    }

    /** Consulta el historial de movimientos de un credito. */
    @GetMapping("/{id}/movements")
    public Flux<CreditMovement> getMovements(@PathVariable String id) {
        return creditService.getMovements(id);
    }

    /**
     * Indica si un cliente ya tiene una tarjeta de credito activa con el
     * banco. Consumido internamente por account-service (Fase 8) para
     * validar el requisito de "tarjeta de credito previa" de los
     * perfiles VIP y PYME antes de abrir una cuenta.
     */
    @GetMapping("/customers/{customerId}/has-credit-card")
    public Mono<Boolean> hasActiveCreditCard(@PathVariable String customerId) {
        return creditService.hasActiveCreditCard(customerId);
    }

    /**
     * Reporte general de movimientos de un credito en el intervalo de
     * fechas indicado por el usuario (Fase 9).
     */
    @GetMapping("/{id}/movements/report")
    public Flux<CreditMovement> getMovementsReport(@PathVariable String id,
            @RequestParam Instant startDate, @RequestParam Instant endDate) {
        return creditService.getMovementsReport(id, startDate, endDate);
    }

    /**
     * Reporte con los ultimos 10 movimientos de una tarjeta de credito
     * (Fase 9). El reporte equivalente de tarjeta de debito queda
     * pendiente para cuando se incorpore ese producto (Parte III).
     */
    @GetMapping("/{id}/movements/last-10")
    public Flux<CreditMovement> getLastTenMovements(@PathVariable String id) {
        return creditService.getLastTenCardMovements(id);
    }

    /**
     * Indica si un cliente tiene deuda vencida en algun producto de
     * credito propio (Fase 10). Consumido internamente por
     * {@code account-service} para rechazar la apertura de nuevos
     * productos (cuentas, y localmente tambien nuevos creditos) a ese
     * cliente. Endpoint nuevo de Parte III: expuesto con RxJava en vez de
     * Reactor, como el resto de la funcionalidad nueva.
     */
    @GetMapping("/customers/{customerId}/has-overdue-debt")
    public Single<Boolean> hasOverdueDebt(@PathVariable String customerId) {
        return RxJava3Adapter.monoToSingle(creditService.hasOverdueDebt(customerId));
    }
}
