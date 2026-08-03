package com.banco.creditservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.creditservice.model.CreditStatus;
import com.banco.creditservice.model.PersonalCredit;

import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo de creditos personales. Spring Data filtra
 * automaticamente por el discriminador de tipo (_class) de esta subclase.
 */
public interface PersonalCreditRepository extends ReactiveMongoRepository<PersonalCredit, String> {

    /** Usado para validar que un cliente personal no tenga ya un credito personal activo. */
    Mono<Boolean> existsByCustomerIdAndStatus(String customerId, CreditStatus status);
}
