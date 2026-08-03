package com.banco.accountservice.service;

import java.math.BigDecimal;

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.DebitCard;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operaciones de negocio sobre tarjetas de debito (Fase 11, Parte III):
 * emision asociada a una cuenta bancaria existente y pagos cargados a esa
 * cuenta.
 */
public interface DebitCardService {

    /**
     * Emite una tarjeta de debito para una cuenta bancaria existente, que
     * debe pertenecer al cliente indicado y no tener ya otra tarjeta de
     * debito activa.
     */
    Mono<DebitCard> issue(DebitCard debitCard);

    /** Obtiene todas las tarjetas de debito registradas. */
    Flux<DebitCard> findAll();

    /** Busca una tarjeta de debito por su identificador. */
    Mono<DebitCard> findById(String id);

    /** Registra un pago con la tarjeta, cargado como retiro a la cuenta vinculada. */
    Mono<AccountMovement> pay(String debitCardId, BigDecimal amount);

    /** Historial de pagos realizados con la tarjeta. */
    Flux<AccountMovement> getMovements(String debitCardId);
}
