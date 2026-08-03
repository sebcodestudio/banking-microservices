package com.banco.creditservice.service;

import java.math.BigDecimal;
import java.time.Instant;

import com.banco.creditservice.model.CreditMovement;
import com.banco.creditservice.model.CreditProduct;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Operaciones de negocio sobre productos de credito (personal,
 * empresarial y tarjeta de credito), incluyendo las reglas de
 * otorgamiento y los movimientos de pago y consumo.
 */
public interface CreditService {

    /** Otorga un nuevo credito, validando el tipo de cliente contra customer-service. */
    Mono<CreditProduct> create(CreditProduct credit);

    /** Obtiene todos los creditos registrados. */
    Flux<CreditProduct> findAll();

    /** Busca un credito por su identificador (tambien sirve para consultar el saldo). */
    Mono<CreditProduct> findById(String id);

    /** Actualiza los datos de un credito existente. */
    Mono<CreditProduct> update(String id, CreditProduct credit);

    /** Elimina un credito por su identificador. */
    Mono<Void> delete(String id);

    /**
     * Registra un pago hacia la deuda de un credito personal, empresarial
     * o de tarjeta. {@code payerCustomerId} es opcional (Fase 10): permite
     * que un cliente pague el credito de un tercero; si se omite, se
     * asume que paga el propio titular.
     */
    Mono<CreditMovement> pay(String creditId, BigDecimal amount, String payerCustomerId);

    /** Registra un consumo sobre una tarjeta de credito, validado contra el limite disponible. */
    Mono<CreditMovement> consume(String creditId, BigDecimal amount);

    /** Obtiene el historial de movimientos de un credito. */
    Flux<CreditMovement> getMovements(String creditId);

    /**
     * Indica si un cliente ya tiene una tarjeta de credito activa con el
     * banco. Usado por account-service (Fase 8) para validar el
     * requisito de "tarjeta de credito previa" de los perfiles VIP y PYME.
     */
    Mono<Boolean> hasActiveCreditCard(String customerId);

    /** Reporte general de movimientos de un credito en un intervalo de fechas especificado por el usuario (Fase 9). */
    Flux<CreditMovement> getMovementsReport(String creditId, Instant from, Instant to);

    /**
     * Reporte con los ultimos 10 movimientos de una tarjeta de credito
     * (Fase 9). Rechaza el credito indicado si no es una {@code CreditCard}.
     */
    Flux<CreditMovement> getLastTenCardMovements(String creditId);

    /**
     * Indica si el cliente tiene deuda vencida (balance pendiente con la
     * proxima cuota ya pasada) en algun producto de credito propio
     * (Fase 10). Usado para rechazar el otorgamiento de nuevos productos
     * (creditos o cuentas bancarias) a ese cliente.
     */
    Mono<Boolean> hasOverdueDebt(String customerId);
}
