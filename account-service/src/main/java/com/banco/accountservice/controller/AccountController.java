package com.banco.accountservice.controller;

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

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST con las operaciones CRUD y bancarias del recurso Account.
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** Abre una nueva cuenta (ahorro, corriente o plazo fijo). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<BankAccount> create(@Valid @RequestBody BankAccount account) {
        return accountService.create(account);
    }

    /** Obtiene todas las cuentas registradas. */
    @GetMapping
    public Flux<BankAccount> findAll() {
        return accountService.findAll();
    }

    /** Obtiene una cuenta por su identificador. */
    @GetMapping("/{id}")
    public Mono<BankAccount> findById(@PathVariable String id) {
        return accountService.findById(id);
    }

    /** Actualiza los datos de una cuenta existente. */
    @PutMapping("/{id}")
    public Mono<BankAccount> update(@PathVariable String id, @Valid @RequestBody BankAccount account) {
        return accountService.update(id, account);
    }

    /** Elimina una cuenta por su identificador. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return accountService.delete(id);
    }

    /** Consulta el saldo disponible de una cuenta. */
    @GetMapping("/{id}/balance")
    public Mono<BankAccount> getBalance(@PathVariable String id) {
        return accountService.findById(id);
    }

    /** Registra un deposito sobre la cuenta. */
    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AccountMovement> deposit(@PathVariable String id, @Valid @RequestBody MovementRequest request) {
        return accountService.deposit(id, request.amount());
    }

    /** Registra un retiro sobre la cuenta. */
    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AccountMovement> withdraw(@PathVariable String id, @Valid @RequestBody MovementRequest request) {
        return accountService.withdraw(id, request.amount());
    }

    /** Consulta el historial de movimientos de una cuenta. */
    @GetMapping("/{id}/movements")
    public Flux<AccountMovement> getMovements(@PathVariable String id) {
        return accountService.getMovements(id);
    }

    /**
     * Transfiere fondos desde esta cuenta hacia otra del mismo cliente o
     * de un tercero del mismo banco (Fase 9).
     */
    @PostMapping("/{id}/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TransferResult> transfer(@PathVariable String id, @Valid @RequestBody TransferRequest request) {
        return accountService.transfer(id, request.destinationAccountId(), request.amount());
    }

    /**
     * Reporte general de movimientos de una cuenta en el intervalo de
     * fechas indicado por el usuario (Fase 9).
     */
    @GetMapping("/{id}/movements/report")
    public Flux<AccountMovement> getMovementsReport(@PathVariable String id,
            @RequestParam Instant startDate, @RequestParam Instant endDate) {
        return accountService.getMovementsReport(id, startDate, endDate);
    }
}
