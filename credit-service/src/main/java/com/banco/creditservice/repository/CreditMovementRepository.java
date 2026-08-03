package com.banco.creditservice.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.creditservice.model.CreditMovement;

import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de movimientos de productos de credito.
 * Solo usa metodos derivados de Spring Data, sin @Query.
 */
public interface CreditMovementRepository extends ReactiveMongoRepository<CreditMovement, String> {

    /** Historial de movimientos de un credito, del mas reciente al mas antiguo. */
    Flux<CreditMovement> findByCreditIdOrderByMovementDateDesc(String creditId);

    /** Reporte de movimientos de un credito en un intervalo de fechas especificado por el usuario (Fase 9), del mas reciente al mas antiguo. */
    Flux<CreditMovement> findByCreditIdAndMovementDateBetweenOrderByMovementDateDesc(String creditId, Instant from, Instant to);
}
