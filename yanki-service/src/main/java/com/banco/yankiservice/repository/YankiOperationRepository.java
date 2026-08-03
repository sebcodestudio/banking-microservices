package com.banco.yankiservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.yankiservice.model.YankiOperation;

import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de operaciones asincronas via Kafka (Fase 12).
 * Solo usa metodos derivados de Spring Data, sin @Query.
 */
public interface YankiOperationRepository extends ReactiveMongoRepository<YankiOperation, String> {

    Mono<YankiOperation> findByCorrelationId(String correlationId);
}
