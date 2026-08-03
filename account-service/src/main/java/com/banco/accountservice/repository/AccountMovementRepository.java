package com.banco.accountservice.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.accountservice.model.AccountMovement;
import com.banco.accountservice.model.MovementType;

import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de movimientos de cuentas bancarias.
 * Solo usa metodos derivados de Spring Data, sin @Query.
 */
public interface AccountMovementRepository extends ReactiveMongoRepository<AccountMovement, String> {

    /** Historial de movimientos de una cuenta, del mas reciente al mas antiguo. */
    Flux<AccountMovement> findByAccountIdOrderByMovementDateDesc(String accountId);

    /** Movimientos de una cuenta dentro de un rango de fechas (para validar limites mensuales). */
    Flux<AccountMovement> findByAccountIdAndMovementDateBetween(String accountId, Instant from, Instant to);

    /** Reporte de movimientos de una cuenta en un intervalo de fechas especificado por el usuario (Fase 9), del mas reciente al mas antiguo. */
    Flux<AccountMovement> findByAccountIdAndMovementDateBetweenOrderByMovementDateDesc(String accountId, Instant from, Instant to);

    /** Historial de movimientos de un tipo dado (p.ej. pagos con tarjeta de debito, Fase 11) de una cuenta, del mas reciente al mas antiguo. */
    Flux<AccountMovement> findByAccountIdAndMovementTypeOrderByMovementDateDesc(String accountId, MovementType movementType);
}
