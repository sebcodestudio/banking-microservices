package com.banco.yankiservice.service;

import java.math.BigDecimal;

import com.banco.yankiservice.model.YankiOperation;
import com.banco.yankiservice.model.YankiTransaction;
import com.banco.yankiservice.model.YankiWallet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operaciones de negocio del monedero Yanki (Fase 12, Parte III).
 */
public interface YankiWalletService {

    /** Registra un monedero nuevo; no requiere que el titular sea cliente del banco. */
    Mono<YankiWallet> register(YankiWallet wallet);

    /** Busca un monedero por su identificador. */
    Mono<YankiWallet> findById(String id);

    /** Transfiere saldo entre dos monederos identificados por su numero de celular. */
    Mono<YankiTransferResult> transfer(String senderPhone, String receiverPhone, BigDecimal amount);

    /**
     * Inicia (via Kafka, de forma asincrona) la asociacion del monedero a
     * una tarjeta de debito de account-service. Devuelve la
     * {@link YankiOperation} en estado {@code PENDING}; el resultado se
     * consulta luego con {@link #getOperation(String)}.
     */
    Mono<YankiOperation> linkDebitCard(String walletId, String debitCardId);

    /** Inicia (via Kafka) una carga de saldo desde la cuenta principal vinculada. */
    Mono<YankiOperation> load(String walletId, BigDecimal amount);

    /** Inicia (via Kafka) un retiro de saldo hacia la cuenta principal vinculada. */
    Mono<YankiOperation> withdraw(String walletId, BigDecimal amount);

    /** Consulta el estado de una operacion asincrona por su correlationId. */
    Mono<YankiOperation> getOperation(String correlationId);

    /** Historial de movimientos del monedero. */
    Flux<YankiTransaction> getMovements(String walletId);
}
