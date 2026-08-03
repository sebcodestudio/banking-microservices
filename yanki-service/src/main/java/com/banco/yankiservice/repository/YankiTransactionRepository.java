package com.banco.yankiservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.yankiservice.model.YankiTransaction;

import reactor.core.publisher.Flux;

/**
 * Repositorio reactivo de movimientos de monederos Yanki (Fase 12).
 * Solo usa metodos derivados de Spring Data, sin @Query.
 */
public interface YankiTransactionRepository extends ReactiveMongoRepository<YankiTransaction, String> {

    /** Historial de movimientos de un monedero, del mas reciente al mas antiguo. */
    Flux<YankiTransaction> findByWalletIdOrderByMovementDateDesc(String walletId);
}
