package com.banco.accountservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.banco.accountservice.model.FixedTermAccount;

/**
 * Repositorio reactivo de cuentas a plazo fijo. Spring Data filtra
 * automaticamente por el discriminador de tipo (_class) de esta subclase.
 */
public interface FixedTermAccountRepository extends ReactiveMongoRepository<FixedTermAccount, String> {
}
