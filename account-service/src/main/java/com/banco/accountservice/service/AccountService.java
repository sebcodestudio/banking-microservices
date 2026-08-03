package com.banco.accountservice.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.banco.accountservice.controller.TransferResult;
import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.BankAccount;
import com.banco.accountservice.model.MovementType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operaciones de negocio sobre cuentas bancarias (ahorro, corriente y
 * plazo fijo), incluyendo las reglas de apertura y los movimientos.
 */
public interface AccountService {

    /** Abre una nueva cuenta, validando el tipo de cliente contra customer-service. */
    Mono<BankAccount> create(BankAccount account);

    /** Obtiene todas las cuentas registradas. */
    Flux<BankAccount> findAll();

    /** Busca una cuenta por su identificador (tambien sirve para consultar el saldo). */
    Mono<BankAccount> findById(String id);

    /** Actualiza los datos de una cuenta existente. */
    Mono<BankAccount> update(String id, BankAccount account);

    /** Elimina una cuenta por su identificador. */
    Mono<Void> delete(String id);

    /** Registra un deposito, validando las reglas propias de cada tipo de cuenta. */
    Mono<AccountMovement> deposit(String accountId, BigDecimal amount);

    /** Registra un retiro, validando fondos y las reglas propias de cada tipo de cuenta. */
    Mono<AccountMovement> withdraw(String accountId, BigDecimal amount);

    /**
     * Registra un pago con tarjeta de debito como un retiro (mismas
     * validaciones y comision por exceso de transacciones) sobre la
     * cuenta vinculada a la tarjeta, pero con {@link MovementType#DEBIT_CARD_PAYMENT}
     * en vez de {@code WITHDRAWAL} (Fase 11). Usado por
     * {@code DebitCardService}, que ya valida la tarjeta antes de llamar.
     */
    Mono<AccountMovement> payWithDebitCard(String accountId, BigDecimal amount);

    /** Obtiene el historial de movimientos de una cuenta. */
    Flux<AccountMovement> getMovements(String accountId);

    /**
     * Transfiere fondos entre cuentas del mismo cliente o hacia una cuenta
     * de un tercero del mismo banco (Fase 9). Aplica sobre la cuenta
     * origen y la destino las mismas reglas y comisiones que un retiro y
     * un deposito respectivamente.
     */
    Mono<TransferResult> transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount);

    /** Reporte general de movimientos de una cuenta en un intervalo de fechas especificado por el usuario (Fase 9). */
    Flux<AccountMovement> getMovementsReport(String accountId, Instant from, Instant to);
}
